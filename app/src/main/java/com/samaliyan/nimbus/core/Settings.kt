package com.samaliyan.nimbus.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the phone's traffic reaches the tunnel. */
enum class ConnMode { VPN, PROXY }

/** The way out. The first four ride Cloudflare WARP; the last two are separate networks. */
enum class Protocol(val usesWarp: Boolean, val usesMasque: Boolean) {
    MASQUE(true, true),
    WIREGUARD(true, false),
    GOOL(true, true),
    MIM(true, true),
    PSIPHON(false, false),
    TOR(false, false),
}

enum class ScanMode(val flag: String) {
    TURBO("turbo"), BALANCED("balanced"), THOROUGH("thorough"), VERIFIED("verified"), IRONCLAD("ironclad")
}

enum class IpMode { V4, V6, DUAL }

/** AUTO leaves the choice to the core (firewall for MASQUE, balanced for WireGuard). */
enum class Noize(val flag: String?) {
    AUTO(null), OFF("off"), LIGHT("light"), FIREWALL("firewall"), BALANCED("balanced"), GFW("gfw"), AGGRESSIVE("aggressive")
}

enum class AppFilter { ALL, ONLY_SELECTED, EXCEPT_SELECTED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Lang(val tag: String) { FA("fa"), EN("en") }

const val DEFAULT_DNS = "1.1.1.1,1.0.0.1"

data class Settings(
    val connMode: ConnMode = ConnMode.VPN,
    val protocol: Protocol = Protocol.MASQUE,
    val http2: Boolean = false,
    // Off by default upstream, but it is what gets the WARP API through Iran's filter.
    val fragment: Boolean = true,
    val scan: ScanMode = ScanMode.BALANCED,
    val ip: IpMode = IpMode.V4,
    val noize: Noize = Noize.AUTO,
    val quickReconnect: Boolean = true,
    val peer: String = "",
    val dns: String = DEFAULT_DNS,
    val exitCountries: String = "",
    val psiphonRegion: String = "",
    val extraArgs: String = "",
    val socksPort: Int = 1819,
    val httpProxy: Boolean = false,
    val httpPort: Int = 1820,
    val allowLan: Boolean = false,
    val appFilter: AppFilter = AppFilter.ALL,
    val selectedApps: Set<String> = emptySet(),
    val bypassLan: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lang: Lang = Lang.FA,
    val autoUpdateCheck: Boolean = true,
) {
    /** Only the fields that change how a connection is made; theme or language do not. */
    fun connectionKey(): Settings = copy(theme = ThemeMode.SYSTEM, lang = Lang.FA, autoUpdateCheck = true)
}

object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val state = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings get() = state.value

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        state.value = load(prefs)
    }

    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        if (next == state.value) return
        state.value = next
        save(prefs, next)
    }

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, fallback: E): E =
        getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

    private fun load(p: SharedPreferences): Settings {
        val d = Settings()
        return Settings(
            connMode = p.enum("connMode", d.connMode),
            protocol = p.enum("protocol", d.protocol),
            http2 = p.getBoolean("http2", d.http2),
            fragment = p.getBoolean("fragment", d.fragment),
            scan = p.enum("scan", d.scan),
            ip = p.enum("ip", d.ip),
            noize = p.enum("noize", d.noize),
            quickReconnect = p.getBoolean("quickReconnect", d.quickReconnect),
            peer = p.getString("peer", d.peer) ?: d.peer,
            dns = p.getString("dns", d.dns) ?: d.dns,
            exitCountries = p.getString("exitCountries", d.exitCountries) ?: d.exitCountries,
            psiphonRegion = p.getString("psiphonRegion", d.psiphonRegion) ?: d.psiphonRegion,
            extraArgs = p.getString("extraArgs", d.extraArgs) ?: d.extraArgs,
            socksPort = p.getInt("socksPort", d.socksPort),
            httpProxy = p.getBoolean("httpProxy", d.httpProxy),
            httpPort = p.getInt("httpPort", d.httpPort),
            allowLan = p.getBoolean("allowLan", d.allowLan),
            appFilter = p.enum("appFilter", d.appFilter),
            selectedApps = p.getStringSet("selectedApps", d.selectedApps)?.toSet() ?: d.selectedApps,
            bypassLan = p.getBoolean("bypassLan", d.bypassLan),
            theme = p.enum("theme", d.theme),
            lang = p.enum("lang", d.lang),
            autoUpdateCheck = p.getBoolean("autoUpdateCheck", d.autoUpdateCheck),
        )
    }

    private fun save(p: SharedPreferences, s: Settings) {
        p.edit()
            .putString("connMode", s.connMode.name)
            .putString("protocol", s.protocol.name)
            .putBoolean("http2", s.http2)
            .putBoolean("fragment", s.fragment)
            .putString("scan", s.scan.name)
            .putString("ip", s.ip.name)
            .putString("noize", s.noize.name)
            .putBoolean("quickReconnect", s.quickReconnect)
            .putString("peer", s.peer)
            .putString("dns", s.dns)
            .putString("exitCountries", s.exitCountries)
            .putString("psiphonRegion", s.psiphonRegion)
            .putString("extraArgs", s.extraArgs)
            .putInt("socksPort", s.socksPort)
            .putBoolean("httpProxy", s.httpProxy)
            .putInt("httpPort", s.httpPort)
            .putBoolean("allowLan", s.allowLan)
            .putString("appFilter", s.appFilter.name)
            .putStringSet("selectedApps", s.selectedApps)
            .putBoolean("bypassLan", s.bypassLan)
            .putString("theme", s.theme.name)
            .putString("lang", s.lang.name)
            .putBoolean("autoUpdateCheck", s.autoUpdateCheck)
            .apply()
    }
}
