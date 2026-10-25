package com.samaliyan.nimbus.update

import org.json.JSONObject

data class Release(val version: String, val notes: String, val apkUrl: String, val apkName: String, val size: Long)

/** Reading GitHub's release data, kept apart from Android so it can be unit tested. */
object ReleaseParser {
    /** Picks the APK built for the first ABI this phone supports, or the universal one. */
    fun parse(json: String, abis: List<String>): Release? {
        val o = JSONObject(json)
        val version = o.optString("tag_name").removePrefix("v")
        if (version.isEmpty()) return null
        val assets = o.optJSONArray("assets") ?: return null
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk") }
        val pick = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.optString("name").endsWith("-$abi.apk") } }
            ?: apks.firstOrNull { it.optString("name").contains("universal") }
            ?: return null
        return Release(
            version = version,
            notes = o.optString("body"),
            apkUrl = pick.optString("browser_download_url"),
            apkName = pick.optString("name"),
            size = pick.optLong("size"),
        )
    }

    /** "1.2.10" > "1.2.9"; a dev build ("0.0.0-dev") never offers an update. */
    fun isNewer(candidate: String, current: String): Boolean {
        if (current.contains("dev")) return false
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
