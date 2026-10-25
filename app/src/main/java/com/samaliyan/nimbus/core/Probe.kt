package com.samaliyan.nimbus.core

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Looks at the tunnel from the inside: which address and country the traffic leaves from. */
object Probe {
    // An address, not a name: the app itself is outside the VPN, so nothing needs resolving.
    private const val TRACE_URL = "https://1.1.1.1/cdn-cgi/trace"

    fun proxy(port: Int) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))

    private fun get(url: String, proxy: Proxy, timeoutMs: Int): String {
        val c = URL(url).openConnection(proxy) as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "Nimbus")
        c.useCaches = false
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parseTrace(text: String): ExitInfo? {
        val map = text.lineSequence().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
        val ip = map["ip"] ?: return null
        return ExitInfo(ip = ip, country = map["loc"] ?: "", colo = map["colo"] ?: "", warp = map["warp"] ?: "")
    }

    fun exit(port: Int, timeoutMs: Int = 15_000): ExitInfo? =
        runCatching { parseTrace(get(TRACE_URL, proxy(port), timeoutMs)) }.getOrNull()

    /** Time of one small HTTPS round trip through the tunnel, after a warm-up request. */
    fun latency(port: Int, timeoutMs: Int = 10_000): Long? = runCatching {
        val p = proxy(port)
        get(TRACE_URL, p, timeoutMs)
        val t0 = System.nanoTime()
        get(TRACE_URL, p, timeoutMs)
        (System.nanoTime() - t0) / 1_000_000
    }.getOrNull()
}
