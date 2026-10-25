package com.samaliyan.nimbus.core

/** Where the bundled executables live on the device. */
data class CorePaths(
    val core: String,
    val psiphon: String?,
    val lyrebird: String?,
    val home: String,
    val tmp: String,
)

data class CoreLaunch(val args: List<String>, val env: Map<String, String>)

/**
 * Turns the user's settings into the Aether command line. Kept free of Android classes so it
 * can be unit tested on the JVM.
 */
object CoreArgs {

    fun bindHost(s: Settings): String = if (s.allowLan) "0.0.0.0" else "127.0.0.1"

    fun build(s: Settings, paths: CorePaths): CoreLaunch {
        val a = mutableListOf<String>()
        val host = bindHost(s)
        a += listOf("--bind", "$host:${s.socksPort}")
        if (s.httpProxy) a += listOf("--http-proxy", "$host:${s.httpPort}")

        when (s.protocol) {
            Protocol.MASQUE -> a += "--masque"
            Protocol.WIREGUARD -> a += "--wg"
            Protocol.GOOL -> a += "--gool"
            Protocol.MIM -> a += "--mim"
            Protocol.PSIPHON -> a += "--psiphon-only"
            Protocol.TOR -> a += "--tor-only"
        }

        if (s.protocol.usesWarp) {
            if (s.protocol.usesMasque) a += if (s.http2) "--h2" else "--h3"
            a += listOf("--scan", s.scan.flag)
            a += when (s.ip) {
                IpMode.V4 -> "-4"
                IpMode.V6 -> "-6"
                IpMode.DUAL -> "--dual"
            }
            s.noize.flag?.let { a += listOf("--noize", it) }
            a += if (s.quickReconnect) "--quick-reconnect" else "--no-quick-reconnect"
            val peer = s.peer.trim()
            if (peer.isNotEmpty()) a += listOf("--peer", peer)
            val exit = s.exitCountries.replace(" ", "")
            if (exit.isNotEmpty()) a += listOf("--exit-loc", exit)
        }
        // The WARP API calls are made by every WARP protocol; fragmenting them is what gets
        // registration through a filter that resets the ClientHello.
        if (s.fragment && s.protocol.usesWarp) a += "--fragment"

        if (s.protocol == Protocol.PSIPHON) {
            val region = s.psiphonRegion.trim().uppercase()
            if (region.isNotEmpty()) a += listOf("--psiphon-region", region)
        }

        val dns = s.dns.replace(" ", "")
        if (dns.isNotEmpty() && dns != DEFAULT_DNS) a += listOf("--dns", dns)

        // A traffic line every second drives the speed shown in proxy mode.
        a += listOf("--stats", "--stats-secs", "1")
        a += splitArgs(s.extraArgs)

        val env = linkedMapOf(
            "HOME" to paths.home,
            "TMPDIR" to paths.tmp,
            "NO_COLOR" to "1",
        )
        paths.psiphon?.let { env["AETHER_PSIPHON_BIN"] = it }
        paths.lyrebird?.let { pt ->
            // lyrebird carries all of these in one binary.
            env["AETHER_TOR_PT"] = listOf("obfs4", "webtunnel", "snowflake", "meek_lite")
                .joinToString(";") { "$it=$pt" }
        }
        return CoreLaunch(listOf(paths.core) + a, env)
    }

    /** Splits a command line the way a shell would for plain words and "quoted" or 'quoted' parts. */
    fun splitArgs(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var inToken = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quote != null && c == quote -> quote = null
                quote != null -> cur.append(c)
                c == '"' || c == '\'' -> { quote = c; inToken = true }
                c == '\\' && i + 1 < text.length -> { cur.append(text[i + 1]); i++; inToken = true }
                c.isWhitespace() -> {
                    if (inToken) { out += cur.toString(); cur.clear(); inToken = false }
                }
                else -> { cur.append(c); inToken = true }
            }
            i++
        }
        if (inToken) out += cur.toString()
        return out
    }
}

/** Checks for the free-text settings, so a typo is caught in the form rather than by the core. */
object Validate {
    private val ipv4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

    fun port(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1024..65535 }

    fun isIpv4(text: String) = ipv4.matches(text)

    fun isIpv6(text: String): Boolean {
        if (!text.contains(':') || text.any { !(it.isLetterOrDigit() || it == ':' || it == '.') }) return false
        if (text.any { it.isLetter() && it.lowercaseChar() !in 'a'..'f' }) return false
        val doubles = text.windowed(2).count { it == "::" }
        if (doubles > 1) return false
        val groups = text.split(':')
        return groups.size in 3..8 && groups.all { it.length <= 4 || isIpv4(it) }
    }

    /** ip:port or [ipv6]:port */
    fun endpoint(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (t.startsWith("[")) {
            val end = t.indexOf("]:")
            if (end < 0) return false
            return isIpv6(t.substring(1, end)) && t.substring(end + 2).toIntOrNull()?.let { it in 1..65535 } == true
        }
        val idx = t.lastIndexOf(':')
        if (idx <= 0) return false
        return isIpv4(t.substring(0, idx)) && t.substring(idx + 1).toIntOrNull()?.let { it in 1..65535 } == true
    }

    fun dnsList(text: String): Boolean {
        val t = text.replace(" ", "")
        if (t.isEmpty()) return true
        return t.split(',').all { isIpv4(it) || isIpv6(it) }
    }

    /** "!IR,RU" blocks those exits, "DE,NL" allows only those. */
    fun exitSpec(text: String): Boolean {
        val t = text.replace(" ", "")
        if (t.isEmpty()) return true
        val body = t.removePrefix("!")
        return body.split(',').all { it.length == 2 && it.all { c -> c in 'A'..'Z' || c in 'a'..'z' } }
    }

    fun region(text: String): Boolean {
        val t = text.trim()
        return t.isEmpty() || (t.length == 2 && t.all { it.isLetter() && it.code < 128 })
    }

    /**
     * Extra options must be balanced in quotes and must not move the proxy: the app checks,
     * shows and tunnels to the address it set itself.
     */
    fun extraArgs(text: String): Boolean {
        var quote: Char? = null
        for (c in text) {
            if (quote != null) { if (c == quote) quote = null } else if (c == '"' || c == '\'') quote = c
        }
        if (quote != null) return false
        val owned = setOf("--bind", "--http-proxy")
        return CoreArgs.splitArgs(text).none { arg -> owned.any { arg == it || arg.startsWith("$it=") } }
    }
}
