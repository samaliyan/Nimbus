package com.samaliyan.nimbus.core

import com.samaliyan.nimbus.update.ReleaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    @Test fun validate() {
        assertTrue(Validate.endpoint(""))
        assertTrue(Validate.endpoint("162.159.198.1:443"))
        assertTrue(Validate.endpoint("[2606:4700:103::1]:443"))
        assertFalse(Validate.endpoint("162.159.198.1"))
        assertFalse(Validate.endpoint("300.1.1.1:443"))
        assertFalse(Validate.endpoint("1.1.1.1:70000"))
        assertFalse(Validate.endpoint("example.com:443"))
        assertTrue(Validate.dnsList("1.1.1.1, 2606:4700:4700::1111"))
        assertFalse(Validate.dnsList("1.1.1"))
        assertTrue(Validate.exitSpec("!IR,RU"))
        assertTrue(Validate.exitSpec("de"))
        assertFalse(Validate.exitSpec("!IRN"))
        assertEquals(1819, Validate.port("1819"))
        assertNull(Validate.port("80"))
        assertNull(Validate.port("70000"))
        assertTrue(Validate.extraArgs("--a \"b c\""))
        assertFalse(Validate.extraArgs("--a \"b c"))
        assertFalse(Validate.extraArgs("--bind 0.0.0.0:1819"))
        assertFalse(Validate.extraArgs("--ech auto --http-proxy=0.0.0.0:8080"))
        assertTrue(Validate.extraArgs("--ech auto --bind-something"))
        assertTrue(Validate.region("DE"))
        assertFalse(Validate.region("DEU"))
    }

    @Test fun trace() {
        val e = Probe.parseTrace("fl=1\nh=1.1.1.1\nip=104.28.1.2\nts=1\nvisit_scheme=https\nloc=DE\ncolo=FRA\nwarp=on\n")!!
        assertEquals("104.28.1.2", e.ip)
        assertEquals("DE", e.country)
        assertEquals("on", e.warp)
        assertNull(Probe.parseTrace("<html>"))
    }

    @Test fun versions() {
        assertTrue(ReleaseParser.isNewer("1.0.10", "1.0.9"))
        assertTrue(ReleaseParser.isNewer("1.1", "1.0.99"))
        assertFalse(ReleaseParser.isNewer("1.0.9", "1.0.9"))
        assertFalse(ReleaseParser.isNewer("1.0.8", "1.0.9"))
        assertFalse(ReleaseParser.isNewer("9.9.9", "0.0.0-dev"))
    }

    @Test fun releaseAssetForAbi() {
        val json = """
            {"tag_name":"v1.0.5","body":"notes","assets":[
              {"name":"Nimbus-1.0.5-universal.apk","browser_download_url":"u","size":3},
              {"name":"Nimbus-1.0.5-armeabi-v7a.apk","browser_download_url":"v7","size":1},
              {"name":"Nimbus-1.0.5-arm64-v8a.apk","browser_download_url":"v8","size":2},
              {"name":"SHA256SUMS.txt","browser_download_url":"s","size":1}]}
        """.trimIndent()
        val r = ReleaseParser.parse(json, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("1.0.5", r.version)
        assertEquals("v8", r.apkUrl)
        assertEquals(2L, r.size)
        assertEquals("u", ReleaseParser.parse(json, listOf("x86"))!!.apkUrl)
        assertEquals("v7", ReleaseParser.parse(json, listOf("armeabi-v7a"))!!.apkUrl)

        val t = ReleaseParser.fromTag("a/b", "v1.0.9", listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("1.0.9", t.version)
        assertEquals("https://github.com/a/b/releases/download/v1.0.9/Nimbus-1.0.9-arm64-v8a.apk", t.apkUrl)
        assertEquals("Nimbus-1.0.9-universal.apk", ReleaseParser.fromTag("a/b", "v1.0.9", listOf("x86"))!!.apkName)
        assertNull(ReleaseParser.fromTag("a/b", "v1/../x", listOf("x86")))
    }
}
