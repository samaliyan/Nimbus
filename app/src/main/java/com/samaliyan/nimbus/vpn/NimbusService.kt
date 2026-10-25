package com.samaliyan.nimbus.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.samaliyan.nimbus.Locales
import com.samaliyan.nimbus.NimbusApp
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.AppFilter
import com.samaliyan.nimbus.core.ConnMode
import com.samaliyan.nimbus.core.ConnectStage
import com.samaliyan.nimbus.core.CoreArgs
import com.samaliyan.nimbus.core.CoreEvent
import com.samaliyan.nimbus.core.CorePaths
import com.samaliyan.nimbus.core.CoreRunner
import com.samaliyan.nimbus.core.Level
import com.samaliyan.nimbus.core.LogParser
import com.samaliyan.nimbus.core.Probe
import com.samaliyan.nimbus.core.Protocol
import com.samaliyan.nimbus.core.Settings
import com.samaliyan.nimbus.core.SettingsStore
import com.samaliyan.nimbus.core.Traffic
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.core.TunnelState
import com.samaliyan.nimbus.core.isActive
import com.samaliyan.nimbus.ui.Format
import com.samaliyan.nimbus.ui.MainActivity
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

/**
 * Owns a connection from start to end: runs the core, and in VPN mode turns the phone's
 * traffic into SOCKS5 for it through hev-socks5-tunnel.
 *
 * Every change of the connection (start, stop, restart, an event from the core) runs on one
 * dedicated thread, one at a time and to its end, so no two of them can interleave.
 */
class NimbusService : VpnService() {

    companion object {
        private const val ACTION_START = "com.samaliyan.nimbus.START"
        private const val ACTION_STOP = "com.samaliyan.nimbus.STOP"
        private const val ACTION_RESTART = "com.samaliyan.nimbus.RESTART"
        private const val NOTIFICATION_ID = 1

        private const val TUN_V4 = "198.18.0.1"
        private const val TUN_V6 = "fc00::1"
        private const val MAPPED_DNS = "198.18.0.2"
        private const val MTU = 8500

        /** True from the service's creation to its destruction. */
        @Volatile private var alive = false

        fun start(context: Context) = send(context, ACTION_START)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun restart(context: Context) = send(context, ACTION_RESTART)

        private fun send(context: Context, action: String) {
            if (action == ACTION_STOP && !alive) {
                // Nothing runs; starting a foreground service only to stop it is pointless.
                if (Tunnel.state.value !is TunnelState.Failed) Tunnel.setState(TunnelState.Idle)
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, NimbusService::class.java).setAction(action))
        }
    }

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "nimbus-control") }
    private val control = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = Channel<Pair<Int, CoreEvent>>(Channel.UNLIMITED)

    // Written only on the control thread; read elsewhere only through `generation`.
    private var runner: CoreRunner? = null
    private var tun: ParcelFileDescriptor? = null
    private var tunnelStarted = false
    private var settings = Settings()
    @Volatile private var generation = 0
    private var ready = false
    private var peer: String? = null
    private var lastError: String? = null
    private val sessionJobs = mutableListOf<Job>()
    private var probeJob: Job? = null
    @Volatile private var lastStartId = 0

    // Proxy mode takes its traffic from the core's own counters.
    private var lastStatsAt = 0L
    private var lastUp = 0L
    private var lastDown = 0L

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Locales.wrap(base))
    }

    /** Text in the language chosen now, which may have changed since the service started. */
    private fun text(): Context = Locales.wrap(this)

    override fun onCreate() {
        super.onCreate()
        alive = true
        SettingsStore.init(this)
        scope.launch(control) {
            for ((gen, event) in events) if (gen == generation) apply(event)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Started as a foreground service: it has to say so right away, whatever it then does.
        goForeground(notification(Tunnel.state.value))
        when (intent?.action) {
            ACTION_STOP -> scope.launch(control) { shutdown(TunnelState.Idle, startId) }
            ACTION_RESTART -> scope.launch(control) {
                teardown()
                begin()
            }
            // ACTION_START, or the system starting an always-on VPN (action android.net.VpnService).
            else -> scope.launch(control) { if (!Tunnel.state.value.isActive) begin() }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // Another VPN took over, or the user turned this one off in system settings.
        val id = lastStartId
        scope.launch(control) { shutdown(TunnelState.Idle, id) }
    }

    override fun onDestroy() {
        alive = false
        scope.cancel()
        // Cleanup waits for a command still running, on the control thread, not the main one.
        executor.execute {
            generation++
            stopNative()
            runner?.stop()
            runner = null
            if (Tunnel.state.value.isActive || Tunnel.state.value == TunnelState.Stopping) {
                Tunnel.setState(TunnelState.Idle)
            }
        }
        executor.shutdown()
        super.onDestroy()
    }

    // ---- lifecycle (control thread only) -----------------------------------------------------

    private fun binaries(): Triple<File, File?, File?> {
        val dir = File(applicationInfo.nativeLibraryDir)
        fun opt(name: String) = File(dir, name).takeIf { it.exists() }
        return Triple(File(dir, "libaether.so"), opt("libpsiphon.so"), opt("liblyrebird.so"))
    }

    private fun installedChosenApps(s: Settings): List<String> = s.selectedApps.filter { pkg ->
        pkg != packageName && runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    private fun begin() {
        generation++
        val gen = generation
        settings = SettingsStore.current
        val t = text()
        Tunnel.startedWith = settings
        Tunnel.resetSession()
        ready = false
        peer = null
        lastError = null
        lastStatsAt = 0L
        Tunnel.setState(TunnelState.Starting(ConnectStage.PREPARING))
        // A shutdown that ran just before may have taken the service out of the foreground.
        goForeground(notification(Tunnel.state.value))

        if (settings.connMode == ConnMode.VPN) {
            if (prepare(this) != null) return fail(t.getString(R.string.err_vpn_permission))
            if (!TProxyService.load()) return fail(t.getString(R.string.err_missing_native))
            if (settings.appFilter == AppFilter.ONLY_SELECTED && installedChosenApps(settings).isEmpty()) {
                return fail(t.getString(R.string.err_no_apps))
            }
        }
        if (settings.httpProxy && settings.httpPort == settings.socksPort) {
            return fail(t.getString(R.string.err_port_clash))
        }

        val (core, psiphon, lyrebird) = binaries()
        if (!core.exists()) return fail(t.getString(R.string.err_missing_native))
        if (settings.protocol == Protocol.PSIPHON && psiphon == null) return fail(t.getString(R.string.err_missing_native))
        if (settings.protocol == Protocol.TOR && lyrebird == null) {
            Tunnel.note("lyrebird is missing; tor can only connect without bridges", Level.WARN)
        }

        val workDir = File(filesDir, "core")
        val tmp = File(cacheDir, "core-tmp").apply { mkdirs() }
        val r = CoreRunner(workDir, listOfNotNull(core, psiphon, lyrebird).map { it.absolutePath })
        r.killStray()

        val busy = listOfNotNull(settings.socksPort, settings.httpPort.takeIf { settings.httpProxy })
            .firstOrNull { !portFree(it) }
        if (busy != null) return fail(t.getString(R.string.err_port_busy, busy))

        val launch = CoreArgs.build(
            settings,
            CorePaths(core.absolutePath, psiphon?.absolutePath, lyrebird?.absolutePath, workDir.absolutePath, tmp.absolutePath),
        )
        Tunnel.note("starting: " + masked(launch.args.drop(1)).joinToString(" "))
        try {
            r.start(
                launch,
                onLine = { raw -> onCoreLine(gen, raw) },
                onExit = { code, requested ->
                    scope.launch(control) { if (gen == generation && !requested) onCoreExit(code) }
                },
            )
            runner = r
        } catch (e: Exception) {
            return fail(t.getString(R.string.err_core_start, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun onCoreLine(gen: Int, raw: String) {
        if (raw.isBlank()) return
        val line = LogParser.parse(raw)
        if (!LogParser.isStats(line)) {
            Tunnel.log(line)
            android.util.Log.i("NimbusCore", raw)
        }
        for (e in LogParser.events(line)) events.trySend(gen to e)
    }

    private fun apply(event: CoreEvent) {
        val state = Tunnel.state.value
        when (event) {
            is CoreEvent.Stage -> if (state is TunnelState.Starting && state.stage != event.stage) {
                Tunnel.setState(TunnelState.Starting(event.stage))
                refreshNotification()
            }
            is CoreEvent.Peer -> {
                peer = event.peer
                when (state) {
                    is TunnelState.Connected -> Tunnel.setState(state.copy(peer = event.peer))
                    is TunnelState.Reconnecting -> Tunnel.setState(state.copy(peer = event.peer))
                    else -> Unit
                }
            }
            is CoreEvent.Listening -> {
                val port = event.address.substringAfterLast(':').toIntOrNull()
                val overlay = settings.protocol == Protocol.TOR || settings.protocol == Protocol.PSIPHON
                if (port == settings.socksPort && !overlay) onReady()
            }
            CoreEvent.OverlayReady -> onReady()
            CoreEvent.Reconnecting -> if (state is TunnelState.Connected) {
                Tunnel.setState(TunnelState.Reconnecting(state.since, state.peer))
                refreshNotification()
            }
            CoreEvent.Recovered -> if (state is TunnelState.Reconnecting) {
                Tunnel.setState(TunnelState.Connected(state.since, peer ?: state.peer))
                refreshNotification()
                probeSoon()
            }
            is CoreEvent.Stats -> if (settings.connMode == ConnMode.PROXY && ready) {
                val now = System.currentTimeMillis()
                val dt = (now - lastStatsAt).coerceAtLeast(1)
                val t = if (lastStatsAt == 0L) Traffic(event.up, event.down)
                else Traffic(
                    event.up, event.down,
                    ((event.up - lastUp).coerceAtLeast(0) * 1000 / dt),
                    ((event.down - lastDown).coerceAtLeast(0) * 1000 / dt),
                )
                lastStatsAt = now; lastUp = event.up; lastDown = event.down
                Tunnel.setTraffic(t)
            }
            is CoreEvent.Fatal -> lastError = LogParser.shortError(event.message)
        }
    }

    private fun onReady() {
        if (ready) return
        ready = true
        if (settings.connMode == ConnMode.VPN) {
            val error = startTun()
            if (error != null) return fail(error)
        }
        Tunnel.setState(TunnelState.Connected(System.currentTimeMillis(), peer))
        Tunnel.note("connected")
        refreshNotification()
        startSessionJobs()
    }

    private fun onCoreExit(code: Int) {
        val t = text()
        val message = lastError?.let { Errors.friendly(t, it) } ?: t.getString(R.string.err_core_exit, code)
        Tunnel.note("core ended with code $code", Level.ERROR)
        fail(message)
    }

    private fun fail(message: String) {
        Tunnel.note(message, Level.ERROR)
        shutdown(TunnelState.Failed(message), lastStartId)
    }

    /** Stops everything this connection started, leaving the service itself running. */
    private fun teardown() {
        generation++
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        probeJob?.cancel()
        probeJob = null
        if (Tunnel.state.value.isActive) Tunnel.setState(TunnelState.Stopping)
        stopNative()
        val r = runner
        runner = null
        r?.stop()
        ready = false
    }

    /**
     * Ends the connection and stops the service, unless a start arrived after [startId]: that
     * start is queued behind this command and must still run.
     */
    private fun shutdown(final: TunnelState, startId: Int) {
        teardown()
        Tunnel.setState(final)
        Tunnel.startedWith = null
        if (startId == lastStartId) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
    }

    private fun stopNative() {
        if (tunnelStarted) {
            runCatching { TProxyService.TProxyStopService() }
            tunnelStarted = false
        }
        runCatching { tun?.close() }
        tun = null
    }

    // ---- VPN interface ----------------------------------------------------------------------

    private fun startTun(): String? {
        val s = settings
        val t = text()
        val b = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(TUN_V4, 32)
            .addRoute("0.0.0.0", 0)
            .addAddress(TUN_V6, 128)
            .addRoute("::", 0)
            // Names are answered by hev with addresses it maps back to the name, so the core
            // receives names and resolves them inside the tunnel.
            .addDnsServer(MAPPED_DNS)
            .setBlocking(false)
            .setMetered(false)
            .setConfigureIntent(openAppIntent())

        if (s.bypassLan && Build.VERSION.SDK_INT >= 33) {
            for ((addr, len) in listOf("10.0.0.0" to 8, "172.16.0.0" to 12, "192.168.0.0" to 16, "169.254.0.0" to 16)) {
                b.excludeRoute(IpPrefix(InetAddress.getByName(addr), len))
            }
        }

        // This app, and with it the core it runs, stays outside the VPN, or the tunnel would carry itself.
        val chosen = installedChosenApps(s)
        try {
            if (s.appFilter == AppFilter.ONLY_SELECTED) {
                chosen.forEach { b.addAllowedApplication(it) }
            } else {
                b.addDisallowedApplication(packageName)
                if (s.appFilter == AppFilter.EXCEPT_SELECTED) chosen.forEach { b.addDisallowedApplication(it) }
            }
        } catch (e: Exception) {
            return t.getString(R.string.err_tun, e.message ?: "")
        }

        val fd = try {
            b.establish()
        } catch (e: Exception) {
            return t.getString(R.string.err_tun, e.message ?: "")
        } ?: return t.getString(R.string.err_vpn_permission)
        tun = fd

        val config = File(filesDir, "tunnel.yml")
        config.writeText(
            """
            tunnel:
              mtu: $MTU
              ipv4: $TUN_V4
              ipv6: '$TUN_V6'
            socks5:
              port: ${s.socksPort}
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              address: $MAPPED_DNS
              port: 53
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            misc:
              task-stack-size: 81920
              log-level: warn
            """.trimIndent() + "\n"
        )
        if (!TProxyService.TProxyStartService(config.absolutePath, fd.fd)) {
            return t.getString(R.string.err_tun, "tun2socks")
        }
        tunnelStarted = true
        return null
    }

    // ---- while connected --------------------------------------------------------------------

    private fun startSessionJobs() {
        val gen = generation
        if (settings.connMode == ConnMode.VPN) {
            sessionJobs += scope.launch {
                var prevUp = 0L
                var prevDown = 0L
                var prevAt = System.currentTimeMillis()
                while (isActive) {
                    delay(1000)
                    if (gen != generation) break
                    val st = runCatching { TProxyService.TProxyGetStats() }.getOrNull() ?: continue
                    val now = System.currentTimeMillis()
                    val dt = (now - prevAt).coerceAtLeast(1)
                    val up = st[1]
                    val down = st[3]
                    if (gen != generation) break
                    Tunnel.setTraffic(
                        Traffic(up, down, (up - prevUp).coerceAtLeast(0) * 1000 / dt, (down - prevDown).coerceAtLeast(0) * 1000 / dt)
                    )
                    prevUp = up; prevDown = down; prevAt = now
                }
            }
        }
        sessionJobs += scope.launch {
            while (isActive) {
                delay(2000)
                // On the control thread, so it cannot race a stop that removes the notification.
                launch(control) { if (gen == generation) refreshNotification() }
            }
        }
        probeSoon()
    }

    /** Checks the exit address now, and again every few minutes while connected. */
    private fun probeSoon() {
        probeJob?.cancel()
        val port = settings.socksPort
        val gen = generation
        probeJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val exit = Probe.exit(port)
                val ms = if (exit != null) Probe.latency(port) else null
                // A probe that outlived its connection must not write into the next one.
                if (gen != generation) break
                if (exit != null) Tunnel.setExit(exit)
                Tunnel.setLatency(ms)
                delay(if (exit != null) 5 * 60_000L else 20_000L)
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    /** Keeps secrets passed through the extra arguments out of the log, which users share. */
    private fun masked(args: List<String>): List<String> {
        val secret = setOf("--access-secret", "--access-token", "--access-id", "--upstream")
        return args.mapIndexed { i, a ->
            when {
                i > 0 && args[i - 1] in secret -> "***"
                secret.any { a.startsWith("$it=") } -> a.substringBefore('=') + "=***"
                else -> a
            }
        }
    }

    private fun portFree(port: Int): Boolean = runCatching {
        ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress(CoreArgs.bindHost(settings), port)) }
    }.isSuccess

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun refreshNotification() {
        val state = Tunnel.state.value
        if (!state.isActive) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: TunnelState): Notification {
        val t = text()
        val title = when (state) {
            is TunnelState.Connected -> t.getString(R.string.state_connected)
            is TunnelState.Reconnecting -> t.getString(R.string.state_reconnecting)
            is TunnelState.Starting -> t.getString(Format.stageText(state.stage))
            else -> t.getString(R.string.state_connecting)
        }
        val body = if (state is TunnelState.Connected) {
            val tr = Tunnel.traffic.value
            t.getString(R.string.notif_traffic, Format.rate(t, tr.downRate), Format.rate(t, tr.upRate))
        } else t.getString(R.string.notif_wait)
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, NimbusService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NimbusApp.CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, t.getString(R.string.action_disconnect), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
