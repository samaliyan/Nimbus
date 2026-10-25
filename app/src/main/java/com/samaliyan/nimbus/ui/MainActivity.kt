package com.samaliyan.nimbus.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.samaliyan.nimbus.Locales
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.ConnMode
import com.samaliyan.nimbus.core.SettingsStore
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.core.TunnelState
import com.samaliyan.nimbus.core.isActive
import com.samaliyan.nimbus.ui.theme.NimbusTheme
import com.samaliyan.nimbus.update.Updater
import com.samaliyan.nimbus.vpn.NimbusService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class Tab { HOME, LOG, SETTINGS }

class MainActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CONNECT = "connect"

        /**
         * An intent that opens the app and connects. It carries a secret kept in this app's
         * own storage, so another app cannot make Nimbus connect by sending the same extra.
         */
        fun connectIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_CONNECT, connectToken(context))

        private fun connectToken(context: Context): String {
            val prefs = context.getSharedPreferences("ui", MODE_PRIVATE)
            return prefs.getString("connectToken", null) ?: java.util.UUID.randomUUID().toString().also {
                prefs.edit().putString("connectToken", it).apply()
            }
        }
    }

    /** Set while the system's VPN consent is shown for a reconnect rather than a first connect. */
    private var restartAfterConsent = false

    private val snackbar = SnackbarHostState()
    private var appliedLang = SettingsStore.current.lang

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (restartAfterConsent) NimbusService.restart(this) else NimbusService.start(this)
        } else {
            lifecycleScope.launch { snackbar.showSnackbar(getString(R.string.err_vpn_permission)) }
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the connection works either way */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Locales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        appliedLang = SettingsStore.current.lang

        lifecycleScope.launch {
            SettingsStore.settings.map { it.lang }.distinctUntilChanged().collect { lang ->
                if (lang != appliedLang) recreate()
            }
        }

        if (savedInstanceState == null) handleConnectExtra(intent)

        if (SettingsStore.current.autoUpdateCheck && !Updater.checkedThisLaunch) {
            Updater.checkedThisLaunch = true
            Updater.startCheck(quiet = true)
        }

        setContent {
            val settings by SettingsStore.settings.collectAsStateWithLifecycle()
            NimbusTheme(settings.theme) {
                AppRoot(
                    snackbar = snackbar,
                    onToggle = ::toggle,
                    onReconnect = ::reconnect,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleConnectExtra(intent)
    }

    private fun handleConnectExtra(intent: Intent?) {
        val token = intent?.getStringExtra(EXTRA_CONNECT) ?: return
        intent.removeExtra(EXTRA_CONNECT)
        // Reopening from recent apps replays the launch intent; that is not a request to connect.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (token == connectToken(this) && !Tunnel.state.value.isActive) connect()
    }

    private fun toggle() {
        when (Tunnel.state.value) {
            TunnelState.Stopping -> Unit // a tap while stopping would be lost; wait for it to finish
            else -> if (Tunnel.state.value.isActive) NimbusService.stop(this) else connect()
        }
    }

    /** Restarts the connection with the current settings, asking for VPN consent if VPN mode needs it. */
    private fun reconnect() {
        if (SettingsStore.current.connMode == ConnMode.VPN) {
            val consent = VpnService.prepare(this)
            if (consent != null) {
                restartAfterConsent = true
                vpnConsent.launch(consent)
                return
            }
        }
        NimbusService.restart(this)
    }

    private fun connect() {
        restartAfterConsent = false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("ui", MODE_PRIVATE)
            if (!prefs.getBoolean("askedNotifications", false)) {
                prefs.edit().putBoolean("askedNotifications", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (SettingsStore.current.connMode == ConnMode.VPN) {
            val consent = VpnService.prepare(this)
            if (consent != null) {
                vpnConsent.launch(consent)
                return
            }
        }
        NimbusService.start(this)
    }
}

@Composable
private fun AppRoot(snackbar: SnackbarHostState, onToggle: () -> Unit, onReconnect: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var appPicker by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = appPicker || tab != Tab.HOME) {
        if (appPicker) appPicker = false else tab = Tab.HOME
    }

    UpdateDialog()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!appPicker) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    NavItem(tab == Tab.HOME, Icons.Outlined.Cloud, R.string.tab_home) { tab = Tab.HOME }
                    NavItem(tab == Tab.LOG, Icons.Outlined.Article, R.string.tab_log) { tab = Tab.LOG }
                    NavItem(tab == Tab.SETTINGS, Icons.Outlined.Tune, R.string.tab_settings) { tab = Tab.SETTINGS }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = if (appPicker) null else tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { target ->
                when (target) {
                    Tab.HOME -> HomeScreen(
                        onToggle = onToggle,
                        onReconnect = onReconnect,
                        onOpenLog = { tab = Tab.LOG },
                        snackbar = snackbar,
                    )
                    Tab.LOG -> LogScreen(snackbar)
                    Tab.SETTINGS -> SettingsScreen(
                        onPickApps = { appPicker = true },
                        snackbar = snackbar,
                    )
                    null -> AppPickerScreen(onBack = { appPicker = false })
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
