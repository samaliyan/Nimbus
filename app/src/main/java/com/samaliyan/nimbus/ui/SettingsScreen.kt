package com.samaliyan.nimbus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samaliyan.nimbus.BuildConfig
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.AppFilter
import com.samaliyan.nimbus.core.ConnMode
import com.samaliyan.nimbus.core.DEFAULT_DNS
import com.samaliyan.nimbus.core.IpMode
import com.samaliyan.nimbus.core.Lang
import com.samaliyan.nimbus.core.Noize
import com.samaliyan.nimbus.core.Protocol
import com.samaliyan.nimbus.core.ScanMode
import com.samaliyan.nimbus.core.Settings
import com.samaliyan.nimbus.core.SettingsStore
import com.samaliyan.nimbus.core.ThemeMode
import com.samaliyan.nimbus.core.Tunnel
import com.samaliyan.nimbus.core.Validate
import com.samaliyan.nimbus.core.isActive
import com.samaliyan.nimbus.ui.theme.LocalNimbus
import com.samaliyan.nimbus.update.Updater
import kotlinx.coroutines.launch
import java.io.File

private enum class Dialog {
    PROTOCOL, TRANSPORT, SCAN, IP, NOIZE, PEER, DNS, EXIT, REGION, EXTRA,
    SOCKS_PORT, HTTP_PORT, APP_FILTER, THEME, LANG, RESET,
}

@Composable
fun SettingsScreen(onPickApps: () -> Unit, snackbar: SnackbarHostState) {
    val s by SettingsStore.settings.collectAsStateWithLifecycle()
    val tunnel by Tunnel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val n = LocalNimbus.current
    val warp = s.protocol.usesWarp
    fun set(f: (Settings) -> Settings) = SettingsStore.update(f)

    val notSet = stringResource(R.string.not_set)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp),
        )

        Section(stringResource(R.string.sec_connection)) {
            SettingRow(stringResource(R.string.set_protocol), protocolName(s.protocol), stringResource(protocolHint(s.protocol))) { dialog = Dialog.PROTOCOL }
            RowDivider()
            SettingRow(
                stringResource(R.string.set_transport),
                stringResource(if (s.http2) R.string.transport_h2 else R.string.transport_h3),
                enabled = s.protocol.usesMasque,
            ) { dialog = Dialog.TRANSPORT }
            RowDivider()
            SettingRow(stringResource(R.string.set_scan), scanLabel(s.scan), enabled = warp) { dialog = Dialog.SCAN }
            RowDivider()
            SettingRow(stringResource(R.string.set_ip), ipLabel(s.ip), enabled = warp) { dialog = Dialog.IP }
            RowDivider()
            SettingRow(stringResource(R.string.set_noize), noizeLabel(s.noize), enabled = warp) { dialog = Dialog.NOIZE }
            RowDivider()
            SwitchRow(stringResource(R.string.set_fragment), s.fragment, stringResource(R.string.set_fragment_hint), enabled = warp) { v -> set { it.copy(fragment = v) } }
            RowDivider()
            SwitchRow(stringResource(R.string.set_quick), s.quickReconnect, stringResource(R.string.set_quick_hint), enabled = warp) { v -> set { it.copy(quickReconnect = v) } }
        }

        Section(stringResource(R.string.sec_vpn)) {
            SettingRow(
                stringResource(R.string.set_apps),
                appFilterLabel(s.appFilter) + if (s.appFilter != AppFilter.ALL) " · " + stringResource(R.string.apps_count, Format.number(context, s.selectedApps.size.toLong())) else "",
                enabled = s.connMode == ConnMode.VPN,
            ) { dialog = Dialog.APP_FILTER }
            if (s.appFilter != AppFilter.ALL) {
                RowDivider()
                SettingRow(stringResource(R.string.set_choose_apps), enabled = s.connMode == ConnMode.VPN, onClick = onPickApps)
            }
            RowDivider()
            SwitchRow(stringResource(R.string.set_bypass_lan), s.bypassLan, stringResource(R.string.set_bypass_lan_hint), enabled = s.connMode == ConnMode.VPN) { v -> set { it.copy(bypassLan = v) } }
        }

        Section(stringResource(R.string.sec_proxy)) {
            SettingRow(stringResource(R.string.set_socks_port), s.socksPort.toString(), ltrValue = true) { dialog = Dialog.SOCKS_PORT }
            RowDivider()
            SwitchRow(stringResource(R.string.set_http), s.httpProxy, stringResource(R.string.set_http_hint)) { v ->
                // Turning HTTP on must not leave it on the SOCKS port.
                set { it.copy(httpProxy = v, httpPort = if (v && it.httpPort == it.socksPort) if (it.socksPort < 65535) it.socksPort + 1 else it.socksPort - 1 else it.httpPort) }
            }
            if (s.httpProxy) {
                RowDivider()
                SettingRow(stringResource(R.string.set_http_port), s.httpPort.toString(), ltrValue = true) { dialog = Dialog.HTTP_PORT }
            }
            RowDivider()
            SwitchRow(stringResource(R.string.set_lan), s.allowLan, stringResource(R.string.set_lan_hint)) { v -> set { it.copy(allowLan = v) } }
        }

        Section(stringResource(R.string.sec_advanced)) {
            SettingRow(stringResource(R.string.set_peer), s.peer.ifBlank { stringResource(R.string.peer_auto) }, ltrValue = s.peer.isNotBlank(), enabled = warp) { dialog = Dialog.PEER }
            RowDivider()
            SettingRow(stringResource(R.string.set_dns), s.dns.ifBlank { DEFAULT_DNS }, ltrValue = true) { dialog = Dialog.DNS }
            RowDivider()
            SettingRow(stringResource(R.string.set_exit), s.exitCountries.ifBlank { notSet }, ltrValue = s.exitCountries.isNotBlank(), enabled = warp) { dialog = Dialog.EXIT }
            if (s.protocol == Protocol.PSIPHON) {
                RowDivider()
                SettingRow(stringResource(R.string.set_region), s.psiphonRegion.ifBlank { notSet }, ltrValue = s.psiphonRegion.isNotBlank()) { dialog = Dialog.REGION }
            }
            RowDivider()
            SettingRow(stringResource(R.string.set_extra), s.extraArgs.ifBlank { notSet }, ltrValue = s.extraArgs.isNotBlank()) { dialog = Dialog.EXTRA }
            RowDivider()
            SettingRow(
                stringResource(R.string.set_reset),
                hint = stringResource(R.string.set_reset_hint),
                enabled = !tunnel.isActive,
            ) { dialog = Dialog.RESET }
        }

        Section(stringResource(R.string.sec_look)) {
            SettingRow(stringResource(R.string.set_theme), themeLabel(s.theme)) { dialog = Dialog.THEME }
            RowDivider()
            SettingRow(stringResource(R.string.set_lang), if (s.lang == Lang.FA) "فارسی" else "English") { dialog = Dialog.LANG }
        }

        Section(stringResource(R.string.sec_about)) {
            SettingRow(stringResource(R.string.app_name), stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.CORE_VERSION), ltrValue = false) {}
            RowDivider()
            SettingRow(stringResource(R.string.set_check_update)) { Updater.startCheck(quiet = false) }
            RowDivider()
            SwitchRow(stringResource(R.string.set_auto_update), s.autoUpdateCheck) { v -> set { it.copy(autoUpdateCheck = v) } }
            RowDivider()
            SettingRow(stringResource(R.string.about_source), "github.com/${BuildConfig.UPDATE_REPO}", ltrValue = true) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}")))
            }
            RowDivider()
            SettingRow(stringResource(R.string.about_core), stringResource(R.string.about_core_hint)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CluvexStudio/Aether")))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            color = n.muted,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }

    val close = { dialog = null }
    val badValue = stringResource(R.string.err_bad_value)
    when (dialog) {
        Dialog.PROTOCOL -> ChoiceDialog(
            stringResource(R.string.set_protocol),
            Protocol.entries.map { Choice(it, protocolName(it), stringResource(protocolHint(it))) },
            s.protocol, { v -> set { it.copy(protocol = v) } }, close,
        )
        Dialog.TRANSPORT -> ChoiceDialog(
            stringResource(R.string.set_transport),
            listOf(
                Choice(false, stringResource(R.string.transport_h3), stringResource(R.string.transport_h3_hint)),
                Choice(true, stringResource(R.string.transport_h2), stringResource(R.string.transport_h2_hint)),
            ),
            s.http2, { v -> set { it.copy(http2 = v) } }, close,
        )
        Dialog.SCAN -> ChoiceDialog(
            stringResource(R.string.set_scan),
            ScanMode.entries.map { Choice(it, scanLabel(it), scanHint(it)) },
            s.scan, { v -> set { it.copy(scan = v) } }, close,
        )
        Dialog.IP -> ChoiceDialog(
            stringResource(R.string.set_ip),
            IpMode.entries.map { Choice(it, ipLabel(it)) },
            s.ip, { v -> set { it.copy(ip = v) } }, close,
        )
        Dialog.NOIZE -> ChoiceDialog(
            stringResource(R.string.set_noize),
            Noize.entries.map { Choice(it, noizeLabel(it), if (it == Noize.GFW) stringResource(R.string.noize_gfw_hint) else null) },
            s.noize, { v -> set { it.copy(noize = v) } }, close,
        )
        Dialog.APP_FILTER -> ChoiceDialog(
            stringResource(R.string.set_apps),
            AppFilter.entries.map { Choice(it, appFilterLabel(it)) },
            s.appFilter, { v ->
                set { it.copy(appFilter = v) }
                if (v != AppFilter.ALL && s.selectedApps.isEmpty()) onPickApps()
            }, close,
        )
        Dialog.THEME -> ChoiceDialog(
            stringResource(R.string.set_theme),
            ThemeMode.entries.map { Choice(it, themeLabel(it)) },
            s.theme, { v -> set { it.copy(theme = v) } }, close,
        )
        Dialog.LANG -> ChoiceDialog(
            stringResource(R.string.set_lang),
            listOf(Choice(Lang.FA, "فارسی"), Choice(Lang.EN, "English")),
            s.lang, { v -> set { it.copy(lang = v) } }, close,
        )
        Dialog.PEER -> TextDialog(
            stringResource(R.string.set_peer), s.peer, stringResource(R.string.peer_hint), "162.159.198.1:443",
            valid = Validate::endpoint, errorText = badValue, onSave = { v -> set { it.copy(peer = v) } }, onDismiss = close,
        )
        Dialog.DNS -> TextDialog(
            stringResource(R.string.set_dns), s.dns, stringResource(R.string.dns_hint), DEFAULT_DNS,
            valid = Validate::dnsList, errorText = badValue,
            onSave = { v -> set { it.copy(dns = v.ifBlank { DEFAULT_DNS }) } }, onDismiss = close,
        )
        Dialog.EXIT -> TextDialog(
            stringResource(R.string.set_exit), s.exitCountries, stringResource(R.string.exit_hint), "!IR",
            valid = Validate::exitSpec, errorText = badValue, onSave = { v -> set { it.copy(exitCountries = v.uppercase()) } }, onDismiss = close,
        )
        Dialog.REGION -> TextDialog(
            stringResource(R.string.set_region), s.psiphonRegion, stringResource(R.string.region_hint), "DE",
            valid = Validate::region, errorText = badValue, onSave = { v -> set { it.copy(psiphonRegion = v.uppercase()) } }, onDismiss = close,
        )
        Dialog.EXTRA -> TextDialog(
            stringResource(R.string.set_extra), s.extraArgs, stringResource(R.string.extra_hint), "--ech auto",
            valid = Validate::extraArgs, errorText = badValue, onSave = { v -> set { it.copy(extraArgs = v) } }, onDismiss = close,
        )
        Dialog.SOCKS_PORT -> TextDialog(
            stringResource(R.string.set_socks_port), s.socksPort.toString(), stringResource(R.string.port_hint), "1819", numeric = true,
            valid = { v -> Validate.port(v) != null && Validate.port(v) != s.httpPort.takeIf { s.httpProxy } },
            errorText = badValue, onSave = { v -> set { it.copy(socksPort = Validate.port(v)!!) } }, onDismiss = close,
        )
        Dialog.HTTP_PORT -> TextDialog(
            stringResource(R.string.set_http_port), s.httpPort.toString(), stringResource(R.string.port_hint), "1820", numeric = true,
            valid = { v -> Validate.port(v) != null && Validate.port(v) != s.socksPort },
            errorText = badValue, onSave = { v -> set { it.copy(httpPort = Validate.port(v)!!) } }, onDismiss = close,
        )
        Dialog.RESET -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.set_reset)) },
            text = { Text(stringResource(R.string.reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    val dir = File(context.filesDir, "core")
                    dir.listFiles()?.filter { it.isFile && it.name.endsWith(".toml") }?.forEach { it.delete() }
                    Tunnel.note("account and saved gateways removed")
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.reset_done)) }
                    close()
                }) { Text(stringResource(R.string.reset_action), color = n.error) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun scanLabel(m: ScanMode) = stringResource(
    when (m) {
        ScanMode.TURBO -> R.string.scan_turbo
        ScanMode.BALANCED -> R.string.scan_balanced
        ScanMode.THOROUGH -> R.string.scan_thorough
        ScanMode.VERIFIED -> R.string.scan_verified
        ScanMode.IRONCLAD -> R.string.scan_ironclad
    }
)

@Composable
private fun scanHint(m: ScanMode) = stringResource(
    when (m) {
        ScanMode.TURBO -> R.string.scan_turbo_hint
        ScanMode.BALANCED -> R.string.scan_balanced_hint
        ScanMode.THOROUGH -> R.string.scan_thorough_hint
        ScanMode.VERIFIED -> R.string.scan_verified_hint
        ScanMode.IRONCLAD -> R.string.scan_ironclad_hint
    }
)

@Composable
private fun ipLabel(m: IpMode) = stringResource(
    when (m) {
        IpMode.V4 -> R.string.ip_v4
        IpMode.V6 -> R.string.ip_v6
        IpMode.DUAL -> R.string.ip_dual
    }
)

@Composable
private fun noizeLabel(m: Noize) = stringResource(
    when (m) {
        Noize.AUTO -> R.string.noize_auto
        Noize.OFF -> R.string.noize_off
        Noize.LIGHT -> R.string.noize_light
        Noize.FIREWALL -> R.string.noize_firewall
        Noize.BALANCED -> R.string.noize_balanced
        Noize.GFW -> R.string.noize_gfw
        Noize.AGGRESSIVE -> R.string.noize_aggressive
    }
)

@Composable
private fun appFilterLabel(f: AppFilter) = stringResource(
    when (f) {
        AppFilter.ALL -> R.string.apps_all
        AppFilter.ONLY_SELECTED -> R.string.apps_only
        AppFilter.EXCEPT_SELECTED -> R.string.apps_except
    }
)

@Composable
private fun themeLabel(t: ThemeMode) = stringResource(
    when (t) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)
