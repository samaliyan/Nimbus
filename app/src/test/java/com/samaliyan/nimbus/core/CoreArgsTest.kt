package com.samaliyan.nimbus.core

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreArgsTest {
    private val paths = CorePaths("/lib/libaether.so", "/lib/libpsiphon.so", "/lib/liblyrebird.so", "/data/core", "/cache")

    private fun args(s: Settings) = CoreArgs.build(s, paths).args

    @Test fun defaultsAreMasqueOverHttp3WithFragment() {
        val a = args(Settings())
        assertEquals("/lib/libaether.so", a.first())
        assertEquals(listOf("--bind", "127.0.0.1:1819"), a.subList(1, 3))
        assertTrue("--masque" in a)
        assertTrue("--h3" in a)
        assertTrue("--fragment" in a)
        assertTrue("-4" in a)
        assertTrue("--quick-reconnect" in a)
        assertFalse("--noize" in a)
        assertFalse("--dns" in a)
        assertFalse("--peer" in a)
        assertTrue(a.containsAll(listOf("--stats", "--stats-secs", "1")))
    }

    @Test fun lanAndHttpProxy() {
        val a = args(Settings(allowLan = true, httpProxy = true, httpPort = 8080, socksPort = 2000))
        assertEquals(listOf("--bind", "0.0.0.0:2000"), a.subList(1, 3))
        val i = a.indexOf("--http-proxy")
        assertEquals("0.0.0.0:8080", a[i + 1])
    }

    @Test fun wireguardHasNoMasqueTransport() {
        val a = args(Settings(protocol = Protocol.WIREGUARD, http2 = true))
        assertTrue("--wg" in a)
        assertFalse("--h2" in a)
        assertFalse("--h3" in a)
    }

    @Test fun goolOverHttp2() {
        val a = args(Settings(protocol = Protocol.GOOL, http2 = true))
        assertTrue("--gool" in a)
        assertTrue("--h2" in a)
    }

    @Test fun psiphonSkipsWarpOptions() {
        val a = args(Settings(protocol = Protocol.PSIPHON, psiphonRegion = "de", peer = "1.2.3.4:443", exitCountries = "!IR"))
        assertTrue("--psiphon-only" in a)
        assertEquals("DE", a[a.indexOf("--psiphon-region") + 1])
        assertFalse("--scan" in a)
        assertFalse("--peer" in a)
        assertFalse("--exit-loc" in a)
        assertFalse("--fragment" in a)
        assertFalse("--quick-reconnect" in a)
    }

    @Test fun httpProxyFlagFollowsTheRoute() {
        assertTrue("--tor-http" in args(Settings(protocol = Protocol.TOR, httpProxy = true)))
        assertTrue("--psiphon-http" in args(Settings(protocol = Protocol.PSIPHON, httpProxy = true)))
        val a = args(Settings(protocol = Protocol.TOR, httpProxy = true))
        assertFalse("--http-proxy" in a)
    }

    @Test fun psiphonGetsDnsServers() {
        val withConfig = paths.copy(psiphonConfig = "/data/core/psiphon.json")
        val a = CoreArgs.build(Settings(protocol = Protocol.PSIPHON), withConfig).args
        assertEquals("/data/core/psiphon.json", a[a.indexOf("--psiphon-config") + 1])
        assertFalse("--psiphon-config" in CoreArgs.build(Settings(protocol = Protocol.MASQUE), withConfig).args)
        // A config of the user's own wins.
        val own = CoreArgs.build(Settings(protocol = Protocol.PSIPHON, extraArgs = "--psiphon-config /x.json"), withConfig).args
        assertEquals(1, own.count { it == "--psiphon-config" })

        val json = org.json.JSONObject(CoreArgs.psiphonOverrides(listOf("192.168.1.1", "fe80::1%wlan0", "192.168.1.1")))
        assertEquals("[\"192.168.1.1\"]", json.getJSONArray("DNSResolverAlternateServers").toString())
        val fallback = org.json.JSONObject(CoreArgs.psiphonOverrides(emptyList()))
        assertEquals(3, fallback.getJSONArray("DNSResolverAlternateServers").length())
    }

    @Test fun psiphonMode() {
        assertFalse("--psiphon-mode" in args(Settings(protocol = Protocol.PSIPHON)))
        val a = args(Settings(protocol = Protocol.PSIPHON, psiphonMode = PsiphonMode.DIRECT))
        assertEquals("direct", a[a.indexOf("--psiphon-mode") + 1])
        assertFalse("--psiphon-mode" in args(Settings(protocol = Protocol.MASQUE, psiphonMode = PsiphonMode.CDN)))
    }

    @Test fun torOnly() {
        val launch = CoreArgs.build(Settings(protocol = Protocol.TOR), paths)
        assertTrue("--tor-only" in launch.args)
        assertEquals(
            "obfs4=/lib/liblyrebird.so;webtunnel=/lib/liblyrebird.so;snowflake=/lib/liblyrebird.so;meek_lite=/lib/liblyrebird.so",
            launch.env["AETHER_TOR_PT"],
        )
        assertEquals("/lib/libpsiphon.so", launch.env["AETHER_PSIPHON_BIN"])
        assertEquals("/data/core", launch.env["HOME"])
    }

    @Test fun advancedOptions() {
        val a = args(
            Settings(
                scan = ScanMode.IRONCLAD, ip = IpMode.DUAL, noize = Noize.GFW, quickReconnect = false,
                peer = " 162.159.198.1:443 ", exitCountries = "de, nl", dns = "8.8.8.8, 9.9.9.9", fragment = false,
                extraArgs = "--ech auto --masque-sni \"www.example.com\"",
            )
        )
        assertEquals("ironclad", a[a.indexOf("--scan") + 1])
        assertTrue("--dual" in a)
        assertEquals("gfw", a[a.indexOf("--noize") + 1])
        assertTrue("--no-quick-reconnect" in a)
        assertEquals("162.159.198.1:443", a[a.indexOf("--peer") + 1])
        assertEquals("de,nl", a[a.indexOf("--exit-loc") + 1])
        assertEquals("8.8.8.8,9.9.9.9", a[a.indexOf("--dns") + 1])
        assertFalse("--fragment" in a)
        assertEquals(listOf("--ech", "auto", "--masque-sni", "www.example.com"), a.takeLast(4))
    }

    @Test fun splitArgsHandlesQuotes() {
        assertEquals(listOf("a", "b c", "d'e", ""), CoreArgs.splitArgs("  a \"b c\" \"d'e\" '' "))
        assertEquals(emptyList<String>(), CoreArgs.splitArgs("   "))
        assertEquals(listOf("x y"), CoreArgs.splitArgs("x\\ y"))
    }

    /**
     * Writes the command line of many setting combinations, so CI can run the real core binary
     * with each one and fail on any option it does not know.
     */
    @Test fun exportArgumentSets() {
        val sets = mutableListOf<Settings>()
        for (p in Protocol.entries) for (h2 in listOf(false, true)) for (lan in listOf(false, true)) {
            sets += Settings(protocol = p, http2 = h2, allowLan = lan, httpProxy = lan)
        }
        for (m in ScanMode.entries) sets += Settings(scan = m)
        for (ip in IpMode.entries) sets += Settings(ip = ip)
        for (nz in Noize.entries) sets += Settings(noize = nz, protocol = Protocol.WIREGUARD)
        sets += Settings(peer = "162.159.198.1:443", exitCountries = "!IR,RU", dns = "8.8.8.8", quickReconnect = false, fragment = false)
        sets += Settings(protocol = Protocol.PSIPHON, psiphonRegion = "DE")
        for (m in PsiphonMode.entries) sets += Settings(protocol = Protocol.PSIPHON, psiphonMode = m)
        sets += Settings(protocol = Protocol.MIM, peer = "[2606:4700:103::1]:443")

        val out = File(System.getProperty("user.dir"), "build/core-args.jsonl")
        out.parentFile.mkdirs()
        out.writeText(sets.joinToString("\n") { JSONArray(args(it).drop(1)).toString() } + "\n")
        assertTrue(out.length() > 0)
    }
}
