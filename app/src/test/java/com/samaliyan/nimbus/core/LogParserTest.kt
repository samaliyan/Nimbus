package com.samaliyan.nimbus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogParserTest {
    private fun events(raw: String) = LogParser.events(LogParser.parse(raw))

    @Test fun parsesCoreLine() {
        val l = LogParser.parse("[2026-10-25T07:46:16.154Z INFO  aether] Aether v2.4.0")
        assertEquals("07:46:16", l.time)
        assertEquals(Level.INFO, l.level)
        assertEquals("Aether v2.4.0", l.message)
        val w = LogParser.parse("[2026-10-25T07:46:16.165Z WARN  aether::account] [!] registration retry 1/4")
        assertEquals(Level.WARN, w.level)
        assertEquals("[!] registration retry 1/4", w.message)
    }

    @Test fun listeningMeansReady() {
        val e = events("[2026-10-25T07:46:16.154Z INFO  aether::socks] [+] socks5 server listening on 127.0.0.1:1819")
        assertTrue(e.contains(CoreEvent.Listening("127.0.0.1:1819")))
        val http = events("[2026-10-25T07:46:16.154Z INFO  aether::socks] [+] http proxy listening on 127.0.0.1:1820")
        assertTrue(http.none { it is CoreEvent.Listening })
    }

    @Test fun overlayReady() {
        assertTrue(events("[t INFO  aether::tor] [+] tor is ready; 127.0.0.1:1819 leaves through tor").contains(CoreEvent.OverlayReady))
        assertTrue(events("[t INFO  aether::psiphon] [+] psiphon is ready; 127.0.0.1:1819 leaves through psiphon").contains(CoreEvent.OverlayReady))
    }

    @Test fun stagesAndPeers() {
        assertTrue(events("[t INFO  aether] [+] no masque identity found; provisioning dedicated masque account")
            .contains(CoreEvent.Stage(ConnectStage.REGISTERING)))
        assertTrue(events("[t INFO  aether::prober] [+] candidate ok 162.159.198.2:443 rtt=80ms")
            .contains(CoreEvent.Stage(ConnectStage.SCANNING)))
        assertTrue(events("[t INFO  aether] [*] establishing outer MASQUE tunnel to 1.2.3.4:443...")
            .contains(CoreEvent.Stage(ConnectStage.CONNECTING)))
        assertTrue(events("[t INFO  aether] [+] using cloudflare edge 162.159.198.1:443")
            .contains(CoreEvent.Peer("162.159.198.1:443")))
        assertTrue(events("[t INFO  aether::prober] [+] best gateway 162.159.198.1:443 rtt=12ms")
            .contains(CoreEvent.Peer("162.159.198.1:443")))
    }

    @Test fun reconnectAndRecover() {
        assertTrue(events("[t WARN  aether] [-] MASQUE tunnel ended: timeout; reconnecting").contains(CoreEvent.Reconnecting))
        assertTrue(events("[t WARN  aether] [-] no usable MASQUE gateway found: x; rescanning shortly").contains(CoreEvent.Reconnecting))
        // Picking a gateway is not yet a working tunnel.
        assertTrue(events("[t INFO  aether] [+] using cloudflare edge 1.2.3.4:443").none { it == CoreEvent.Recovered })
        assertTrue(events("[t INFO  aether::psiphon] [+] psiphon reached a server at 1.2.3.4:443").contains(CoreEvent.Recovered))
    }

    @Test fun stats() {
        val e = events("[2026-10-25T07:46:18.157Z INFO  aether::stats] [=] up 1.5 KiB down 2.0 MiB uptime 00:00:02")
        assertEquals(listOf(CoreEvent.Stats(1536, 2 * 1024 * 1024)), e)
        assertTrue(LogParser.isStats(LogParser.parse("[x INFO  aether::stats] [=] up 0 B down 0 B uptime 00:00:02")))
        assertEquals(0L, LogParser.bytes("0 B"))
        assertNull(LogParser.bytes("lots"))
    }

    @Test fun fatal() {
        val e = events("Error: Api(\"registration: api: connect to api.devices.cloudflare.com: failed\")")
        val f = e.single() as CoreEvent.Fatal
        assertEquals("registration: api: connect to api.devices.cloudflare.com: failed", LogParser.shortError(f.message))
        assertEquals("unknown option '--x'", LogParser.shortError("unknown option '--x'\n\nUsage: ..."))
        // What the core really prints: one line, the usage after escaped newlines.
        assertEquals(
            "unknown option '--bogus'",
            LogParser.shortError("Other(\"unknown option '--bogus'\\n\\nAether — a censorship circumvention client.\\n\")"),
        )
    }
}
