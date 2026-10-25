package com.samaliyan.nimbus.vpn

import android.content.Context
import com.samaliyan.nimbus.R

/** Puts the core's last error into words a user can act on, keeping the original for the log. */
object Errors {
    fun friendly(context: Context, raw: String): String {
        val l = raw.lowercase()
        val hint = when {
            l.contains("unknown option") || l.contains("requires a value") -> R.string.err_hint_args
            l.contains("address already in use") || l.contains("addrinuse") -> R.string.err_hint_port
            l.contains("refused this network") || Regex("""\b(403|429)\b""").containsMatchIn(l) ||
                l.contains("rate limit") -> R.string.err_hint_refused
            l.contains("lookup address") || (l.contains("dns") && l.contains("fail")) -> R.string.err_hint_dns
            l.contains("registration") || l.contains("api.cloudflareclient") || l.contains("api.devices") ->
                R.string.err_hint_register
            l.contains("no usable") || l.contains("deadline") || l.contains("no gateway") ->
                R.string.err_hint_scan
            l.contains("exit") && l.contains("countr") -> R.string.err_hint_exit
            else -> null
        }
        return if (hint == null) raw else context.getString(hint) + "\n" + raw
    }
}
