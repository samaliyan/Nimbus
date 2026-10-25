package com.samaliyan.nimbus.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.samaliyan.nimbus.BuildConfig
import com.samaliyan.nimbus.core.Probe
import com.samaliyan.nimbus.core.SettingsStore
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.core.TunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlin.coroutines.coroutineContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val done: Long, val total: Long) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Finds a newer release of this app on GitHub, downloads the APK for this phone and installs it. */
object Updater {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    // Work outlives the screen that asked for it, so turning the phone does not cancel a download.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var job: kotlinx.coroutines.Job? = null

    fun startCheck(quiet: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch { check(quiet) }
    }

    fun startDownload(context: Context, release: Release) {
        if (job?.isActive == true) return
        job = scope.launch { download(context.applicationContext, release) }
    }

    /** Whether the check that runs when the app opens has run in this process. */
    @Volatile var checkedThisLaunch = false

    /** Closes the dialog; a check or download still running is cancelled with it. */
    fun dismiss() {
        job?.cancel()
        job = null
        _state.value = UpdateState.Idle
    }

    /** While connected, GitHub is reached through the tunnel; otherwise directly. */
    private fun proxy(): Proxy {
        val connected = Tunnel.state.value is TunnelState.Connected
        val port = Tunnel.startedWith?.socksPort ?: SettingsStore.current.socksPort
        return if (connected) Probe.proxy(port) else Proxy.NO_PROXY
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection(proxy()) as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Nimbus/${BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    suspend fun check(quiet: Boolean) = withContext(Dispatchers.IO) {
        if (_state.value is UpdateState.Downloading) return@withContext
        if (!quiet) _state.value = UpdateState.Checking
        try {
            val c = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            val body = try {
                if (c.responseCode != 200) throw IllegalStateException("GitHub: HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
            val release = ReleaseParser.parse(body, Build.SUPPORTED_ABIS.toList())
            _state.value = when {
                release == null -> if (quiet) UpdateState.Idle else UpdateState.Failed("no apk in the latest release")
                ReleaseParser.isNewer(release.version, BuildConfig.VERSION_NAME) -> UpdateState.Available(release)
                quiet -> UpdateState.Idle
                else -> UpdateState.UpToDate
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun download(context: Context, release: Release) = withContext(Dispatchers.IO) {
        if (_state.value is UpdateState.Downloading) return@withContext
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)
        val part = File(dir, release.apkName + ".part")
        _state.value = UpdateState.Downloading(release, 0, release.size)
        try {
            var url = release.apkUrl
            var c = open(url)
            c.instanceFollowRedirects = true
            // GitHub sends assets through a redirect to another host, which needs following by hand.
            var hops = 0
            while (c.responseCode in 300..399 && hops < 5) {
                url = c.getHeaderField("Location") ?: break
                c.disconnect()
                c = open(url)
                hops++
            }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
            var done = 0L
            var lastShown = 0L
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastShown > 256 * 1024) {
                            lastShown = done
                            _state.value = UpdateState.Downloading(release, done, total)
                        }
                    }
                }
            }
            c.disconnect()
            if (total > 0 && done != total) throw IllegalStateException("download incomplete")
            if (!part.renameTo(target)) throw IllegalStateException("cannot save the file")
            _state.value = UpdateState.Ready(release, target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Opens the system installer, or the setting that allows it when it is not allowed yet. */
    fun install(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
