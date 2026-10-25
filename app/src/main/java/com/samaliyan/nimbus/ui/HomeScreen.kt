package com.samaliyan.nimbus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.ConnMode
import com.samaliyan.nimbus.core.Probe
import com.samaliyan.nimbus.core.Protocol
import com.samaliyan.nimbus.core.SettingsStore
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.core.TunnelState
import com.samaliyan.nimbus.core.isActive
import com.samaliyan.nimbus.ui.theme.LocalNimbus
import com.samaliyan.nimbus.ui.theme.Vazirmatn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onToggle: () -> Unit,
    onReconnect: () -> Unit,
    onOpenLog: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val state by Tunnel.state.collectAsStateWithLifecycle()
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
    val traffic by Tunnel.traffic.collectAsStateWithLifecycle()
    val exit by Tunnel.exit.collectAsStateWithLifecycle()
    val latency by Tunnel.latency.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = state.isActive

    // A connection started with other settings than the current ones needs a reconnect to use them.
    val started = Tunnel.startedWith
    val stale = active && started != null && started.connectionKey() != settings.connectionKey()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: the name, and how traffic reaches the tunnel.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            SingleChoiceSegmentedButtonRow {
                ConnMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.connMode == mode,
                        onClick = {
                            if (settings.connMode != mode) {
                                SettingsStore.update { it.copy(connMode = mode) }
                                if (active) onReconnect()
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, ConnMode.entries.size),
                        icon = {},
                        label = {
                            Text(
                                stringResource(if (mode == ConnMode.VPN) R.string.mode_vpn else R.string.mode_proxy),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))
        ConnectOrb(state = state, onClick = onToggle)
        Spacer(Modifier.height(28.dp))

        StatusText(state, onOpenLog)

        AnimatedVisibility(
            visible = stale,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_changed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReconnect) { Text(stringResource(R.string.action_reconnect)) }
                }
            }
        }

        AnimatedVisibility(
            visible = state is TunnelState.Connected || state is TunnelState.Reconnecting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(top = 28.dp)) {
                TrafficRow(traffic.downRate, traffic.upRate, traffic.down, traffic.up)
                Spacer(Modifier.height(12.dp))
                ExitRow(
                    exitIp = exit?.ip,
                    country = exit?.country,
                    latency = latency,
                    onTest = {
                        scope.launch {
                            val port = Tunnel.startedWith?.socksPort ?: settings.socksPort
                            Tunnel.setLatency(null)
                            val e = withContext(Dispatchers.IO) { Probe.exit(port) }
                            if (e != null) Tunnel.setExit(e)
                            val ms = withContext(Dispatchers.IO) { if (e != null) Probe.latency(port) else null }
                            Tunnel.setLatency(ms)
                            if (e == null) snackbar.showSnackbar(context.getString(R.string.test_failed))
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        ProtocolPicker(
            selected = settings.protocol,
            onSelect = { p ->
                if (p != settings.protocol) {
                    SettingsStore.update { it.copy(protocol = p) }
                    if (active) onReconnect()
                }
            },
        )

        val proxyShown = settings.connMode == ConnMode.PROXY || settings.allowLan
        if (proxyShown) {
            Spacer(Modifier.height(20.dp))
            ProxyAddresses(
                socks = settings.socksPort,
                http = settings.httpPort.takeIf { settings.httpProxy },
                lan = settings.allowLan,
                onCopy = { text ->
                    copy(context, text)
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.copied)) }
                },
            )
        }
    }
}

// ---- the orb ---------------------------------------------------------------------------------

@Composable
private fun ConnectOrb(state: TunnelState, onClick: () -> Unit) {
    val n = LocalNimbus.current
    val haptics = LocalHapticFeedback.current
    val connected = state is TunnelState.Connected || state is TunnelState.Reconnecting
    val busy = state is TunnelState.Starting || state is TunnelState.Reconnecting || state is TunnelState.Stopping

    // The sky rises through the cloud when the tunnel opens and drains when it closes.
    val fill = remember { Animatable(if (connected) 1f else 0f) }
    LaunchedEffect(connected) {
        fill.animateTo(if (connected) 1f else 0f, tween(if (connected) 900 else 500, easing = FastOutSlowInEasing))
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "angle")
    val breathe by spin.animateFloat(0.97f, 1.0f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")

    val ringColor by animateColorAsState(
        when (state) {
            is TunnelState.Failed -> n.error
            is TunnelState.Reconnecting -> n.warn
            else -> MaterialTheme.colorScheme.primary
        },
        label = "ring",
    )
    val iconColor by animateColorAsState(
        when {
            connected -> Color.White
            state is TunnelState.Failed -> n.error
            busy -> MaterialTheme.colorScheme.primary
            else -> n.muted
        },
        label = "icon",
    )
    val label = stringResource(if (state.isActive) R.string.action_disconnect else R.string.action_connect)

    Box(
        Modifier
            .size(236.dp)
            .scale(if (state is TunnelState.Starting) breathe else 1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(236.dp)) {
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            val inner = r - 18.dp.toPx()

            // Soft halo, brighter once connected.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(n.skyBottom.copy(alpha = 0.10f + 0.25f * fill.value), Color.Transparent),
                    center = c, radius = r,
                ),
                radius = r, center = c,
            )
            // The cloud.
            drawCircle(
                brush = Brush.linearGradient(listOf(n.cloudTop, n.cloudBottom), Offset(c.x, c.y - inner), Offset(c.x, c.y + inner)),
                radius = inner, center = c,
            )
            // The sky, filling from the bottom.
            if (fill.value > 0f) {
                val top = c.y + inner - 2 * inner * fill.value
                clipRect(top = top) {
                    drawCircle(
                        brush = Brush.linearGradient(listOf(n.skyTop, n.skyBottom), Offset(c.x, c.y - inner), Offset(c.x, c.y + inner)),
                        radius = inner, center = c,
                    )
                }
            }
            drawCircle(color = n.hairline.copy(alpha = if (n.dark) 0.9f else 1f), radius = inner, center = c, style = Stroke(1.dp.toPx()))

            // Activity ring: a sweeping arc while working, a full ring on failure.
            val ringR = r - 6.dp.toPx()
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            val topLeft = Offset(c.x - ringR, c.y - ringR)
            val arcSize = Size(ringR * 2, ringR * 2)
            when {
                busy -> drawArc(ringColor, angle - 90f, 90f, false, topLeft, arcSize, style = stroke)
                state is TunnelState.Failed -> drawCircle(ringColor.copy(alpha = 0.7f), ringR, c, style = Stroke(2.dp.toPx()))
            }
        }
        Icon(
            Icons.Outlined.PowerSettingsNew,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun StatusText(state: TunnelState, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val n = LocalNimbus.current
    val title = when (state) {
        TunnelState.Idle -> stringResource(R.string.state_idle)
        is TunnelState.Starting -> stringResource(Format.stageText(state.stage))
        is TunnelState.Connected -> stringResource(R.string.state_connected)
        is TunnelState.Reconnecting -> stringResource(R.string.state_reconnecting)
        TunnelState.Stopping -> stringResource(R.string.state_stopping)
        is TunnelState.Failed -> stringResource(R.string.state_failed)
    }
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    when (state) {
        is TunnelState.Connected, is TunnelState.Reconnecting -> {
            val since = if (state is TunnelState.Connected) state.since else (state as TunnelState.Reconnecting).since
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(since) {
                while (true) { now = System.currentTimeMillis(); delay(1000) }
            }
            Text(
                Format.duration(context, now - since),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = n.muted,
            )
        }
        is TunnelState.Failed -> {
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = n.error,
                textAlign = TextAlign.Center,
                maxLines = 5,
            )
            TextButton(onClick = onOpenLog) { Text(stringResource(R.string.action_view_log)) }
        }
        TunnelState.Idle -> Text(stringResource(R.string.hint_tap), style = MaterialTheme.typography.bodyMedium, color = n.muted)
        is TunnelState.Starting -> Text(stringResource(R.string.hint_wait), style = MaterialTheme.typography.bodyMedium, color = n.muted, textAlign = TextAlign.Center)
        TunnelState.Stopping -> Unit
    }
}

// ---- connected details ---------------------------------------------------------------------

@Composable
private fun TrafficRow(downRate: Long, upRate: Long, down: Long, up: Long) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = 16.dp)) {
            TrafficCell(
                Modifier.weight(1f), Icons.Outlined.ArrowDownward, stringResource(R.string.download),
                Format.rate(context, downRate), Format.bytes(context, down),
            )
            Box(Modifier.width(1.dp).height(48.dp).background(LocalNimbus.current.hairline).align(Alignment.CenterVertically))
            TrafficCell(
                Modifier.weight(1f), Icons.Outlined.ArrowUpward, stringResource(R.string.upload),
                Format.rate(context, upRate), Format.bytes(context, up),
            )
        }
    }
}

@Composable
private fun TrafficCell(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, rate: String, total: String) {
    val n = LocalNimbus.current
    Column(modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = n.muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(rate, style = MaterialTheme.typography.titleMedium)
        Text(total, style = MaterialTheme.typography.bodySmall, color = n.muted)
    }
}

@Composable
private fun ExitRow(exitIp: String?, country: String?, latency: Long?, onTest: () -> Unit) {
    val context = LocalContext.current
    val n = LocalNimbus.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (country.isNullOrBlank()) "🌐" else Format.flag(country).ifEmpty { "🌐" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (country.isNullOrBlank()) stringResource(R.string.exit_unknown) else Format.country(context, country),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    exitIp ?: stringResource(R.string.exit_checking),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = n.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.latency), style = MaterialTheme.typography.labelSmall, color = n.muted)
                Text(
                    latency?.let { stringResource(R.string.ms, Format.number(context, it)) } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (exitIp != null && latency == null) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onTest) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_test))
                }
            }
        }
    }
}

// ---- protocol and proxy ----------------------------------------------------------------------

@Composable
fun protocolName(p: Protocol): String = when (p) {
    Protocol.MASQUE -> "MASQUE"
    Protocol.WIREGUARD -> "WireGuard"
    Protocol.GOOL -> "Gool"
    Protocol.MIM -> "MASQUE²"
    Protocol.PSIPHON -> "Psiphon"
    Protocol.TOR -> "Tor"
}

@Composable
private fun ProtocolPicker(selected: Protocol, onSelect: (Protocol) -> Unit) {
    val n = LocalNimbus.current
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.route), style = MaterialTheme.typography.labelLarge, color = n.muted)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Protocol.entries.forEach { p ->
                FilterChip(
                    selected = p == selected,
                    onClick = { onSelect(p) },
                    label = { Text(protocolName(p), fontFamily = Vazirmatn) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(protocolHint(selected)),
            style = MaterialTheme.typography.bodySmall,
            color = n.muted,
        )
    }
}

fun protocolHint(p: Protocol): Int = when (p) {
    Protocol.MASQUE -> R.string.proto_masque_hint
    Protocol.WIREGUARD -> R.string.proto_wg_hint
    Protocol.GOOL -> R.string.proto_gool_hint
    Protocol.MIM -> R.string.proto_mim_hint
    Protocol.PSIPHON -> R.string.proto_psiphon_hint
    Protocol.TOR -> R.string.proto_tor_hint
}

@Composable
private fun ProxyAddresses(socks: Int, http: Int?, lan: Boolean, onCopy: (String) -> Unit) {
    val n = LocalNimbus.current
    val lanIp = remember(lan) { if (lan) lanAddress() else null }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.proxy_addresses),
                style = MaterialTheme.typography.labelLarge,
                color = n.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            AddressLine("SOCKS5", "127.0.0.1:$socks", onCopy)
            http?.let { AddressLine("HTTP", "127.0.0.1:$it", onCopy) }
            if (lanIp != null) {
                AddressLine("SOCKS5 · LAN", "$lanIp:$socks", onCopy)
                http?.let { AddressLine("HTTP · LAN", "$lanIp:$it", onCopy) }
            }
        }
    }
}

@Composable
private fun AddressLine(kind: String, address: String, onCopy: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCopy(address) }.padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kind, style = MaterialTheme.typography.bodySmall, color = LocalNimbus.current.muted, modifier = Modifier.width(96.dp))
        Text(
            address,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onCopy(address) }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy), modifier = Modifier.size(18.dp))
        }
    }
}

private fun lanAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
}.getOrNull()

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Nimbus", text))
}
