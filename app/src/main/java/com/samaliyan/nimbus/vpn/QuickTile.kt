package com.samaliyan.nimbus.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.samaliyan.nimbus.Locales
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.ConnMode
import com.samaliyan.nimbus.core.SettingsStore
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.core.TunnelState
import com.samaliyan.nimbus.core.isActive
import com.samaliyan.nimbus.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A switch in the quick settings panel. */
class QuickTile : TileService() {
    private var watch: Job? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Locales.wrap(base))
    }

    override fun onStartListening() {
        super.onStartListening()
        watch = CoroutineScope(Dispatchers.Main).launch { Tunnel.state.collect { render(it) } }
    }

    override fun onStopListening() {
        watch?.cancel()
        watch = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        SettingsStore.init(this)
        if (Tunnel.state.value.isActive) {
            NimbusService.stop(this)
            return
        }
        if (SettingsStore.current.connMode == ConnMode.VPN && VpnService.prepare(this) != null) {
            // The system's VPN consent needs an activity; open the app to ask for it.
            openApp()
            return
        }
        NimbusService.start(this)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_CONNECT, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: TunnelState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            is TunnelState.Connected -> Tile.STATE_ACTIVE
            is TunnelState.Starting, is TunnelState.Reconnecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (state) {
                is TunnelState.Connected -> getString(R.string.state_connected)
                is TunnelState.Starting -> getString(R.string.state_connecting)
                is TunnelState.Reconnecting -> getString(R.string.state_reconnecting)
                else -> getString(R.string.state_idle)
            }
        }
        tile.updateTile()
    }
}
