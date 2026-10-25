package com.samaliyan.nimbus.core

enum class Level { DEBUG, INFO, WARN, ERROR }

data class LogLine(val time: String, val level: Level, val message: String)

/** What a line of core output means for the connection. */
sealed interface CoreEvent {
    /** The local proxy is serving on this address. */
    data class Listening(val address: String) : CoreEvent
    /** Tor or Psiphon finished bootstrapping and the proxy leaves through it. */
    data object OverlayReady : CoreEvent
    data class Stage(val stage: ConnectStage) : CoreEvent
    data class Peer(val peer: String) : CoreEvent
    data object Reconnecting : CoreEvent
    data object Recovered : CoreEvent
    data class Stats(val up: Long, val down: Long) : CoreEvent
    data class Fatal(val message: String) : CoreEvent
}

enum class ConnectStage { PREPARING, REGISTERING, SCANNING, CONNECTING }

object LogParser {
    private val lineRe = Regex("""^\[(\S+)\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+[^\]]*\]\s?(.*)$""")
    private val statsRe = Regex("""^\[=] up (.+?) down (.+?) uptime (\S+)""")
    private val listenRe = Regex("""listening on (\S+)$""")
    private val sizeRe = Regex("""^([\d.]+)\s*(B|KiB|MiB|GiB|TiB)$""")
    private val peerRes = listOf(
        Regex("""using cloudflare edge (\S+)"""),
        Regex("""best gateway (\S+?) rtt"""),
        Regex("""using forced peer (\S+)"""),
        Regex("""cached gateway (\S+) still works"""),
        Regex("""gool ready: masque (\S+)"""),
        Regex("""masque-in-masque ready: (\S+)"""),
        Regex("""the assigned endpoint (\S+) works"""),
    )

    fun parse(raw: String): LogLine {
        val m = lineRe.find(raw)
        if (m == null) {
            val level = if (raw.startsWith("Error")) Level.ERROR else Level.INFO
            return LogLine("", level, raw)
        }
        val level = when (m.groupValues[2]) {
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            "DEBUG", "TRACE" -> Level.DEBUG
            else -> Level.INFO
        }
        // "2026-10-25T07:46:16.154Z" -> "07:46:16" (UTC, as the core writes it)
        val time = m.groupValues[1].substringAfter('T').take(8)
        return LogLine(time, level, m.groupValues[3])
    }

    fun isStats(line: LogLine) = line.message.startsWith("[=] up ")

    fun events(line: LogLine): List<CoreEvent> {
        val msg = line.message
        val lower = msg.lowercase()
        val out = mutableListOf<CoreEvent>()

        statsRe.find(msg)?.let { m ->
            val up = bytes(m.groupValues[1])
            val down = bytes(m.groupValues[2])
            if (up != null && down != null) out += CoreEvent.Stats(up, down)
            return out
        }
        if (line.time.isEmpty() && msg.startsWith("Error")) {
            out += CoreEvent.Fatal(msg.removePrefix("Error:").trim())
            return out
        }
        listenRe.find(msg)?.let { m ->
            if (!lower.startsWith("[+] http proxy")) out += CoreEvent.Listening(m.groupValues[1])
        }
        if (lower.contains("tor is ready;") || lower.contains("psiphon is ready;")) out += CoreEvent.OverlayReady
        peerRes.firstNotNullOfOrNull { it.find(msg) }?.let { out += CoreEvent.Peer(it.groupValues[1]) }
        when {
            lower.contains("; reconnecting") || lower.contains("rescanning shortly") ||
                lower.contains("lost its tunnel") -> out += CoreEvent.Reconnecting
            // WARP routes come back with a new "listening" line (see the service); Psiphon and
            // Tor say so themselves. Lines like "using cloudflare edge" come before the tunnel
            // is tried, so they do not mean it works yet.
            lower.contains("psiphon reached a server") || lower.contains("the way out is open") ->
                out += CoreEvent.Recovered
        }
        stageOf(lower)?.let { out += CoreEvent.Stage(it) }
        return out
    }

    private fun stageOf(lower: String): ConnectStage? = when {
        lower.contains("provision") || lower.contains("registration") || lower.contains("registering") ||
            lower.contains("enrolling") -> ConnectStage.REGISTERING
        lower.contains("establishing") || lower.contains("validating") || lower.contains("verifying") ||
            lower.contains("bootstrapping") || lower.contains("starting psiphon") ||
            lower.contains("trying a way out") || lower.contains("retrying last known-good") -> ConnectStage.CONNECTING
        lower.contains("scan") || lower.contains("candidate") || lower.contains("hunting") ||
            lower.contains("gateways") -> ConnectStage.SCANNING
        else -> null
    }

    /** "12.3 MiB" -> bytes. The core rounds to one decimal above a KiB. */
    fun bytes(text: String): Long? {
        val m = sizeRe.find(text.trim()) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2]) {
            "B" -> 1.0
            "KiB" -> 1024.0
            "MiB" -> 1024.0 * 1024
            "GiB" -> 1024.0 * 1024 * 1024
            else -> 1024.0 * 1024 * 1024 * 1024
        }
        return (v * mult).toLong()
    }

    /** The first line of a fatal message, without the usage text the core prints after it. */
    fun shortError(message: String): String {
        val first = message.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: message
        // The core prints its error type around the text: Api("registration: ...") -> registration: ...
        val wrapped = Regex("""^\w+\("(.*)"\)$""").find(first)
        // The usage text after an unknown option arrives on the same line, with escaped newlines.
        return (wrapped?.groupValues?.get(1) ?: first).substringBefore("\\n").replace("\\\"", "\"").trim()
    }
}
