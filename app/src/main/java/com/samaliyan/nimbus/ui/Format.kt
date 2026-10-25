package com.samaliyan.nimbus.ui

import android.content.Context
import androidx.annotation.StringRes
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.ConnectStage
import java.util.Locale

object Format {
    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    fun bytes(context: Context, value: Long): String {
        val units = context.resources.getStringArray(R.array.byte_units)
        var v = value.toDouble()
        var i = 0
        while (v >= 1000 && i < units.size - 1) { v /= 1024; i++ }
        val num = if (i == 0) String.format(locale(context), "%d", value)
        else String.format(locale(context), if (v < 10) "%.1f" else "%.0f", v)
        return "$num ${units[i]}"
    }

    fun rate(context: Context, perSecond: Long): String =
        context.getString(R.string.per_second, bytes(context, perSecond))

    fun duration(context: Context, millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return String.format(locale(context), "%02d:%02d:%02d", h, m, sec)
    }

    fun number(context: Context, n: Long): String = String.format(locale(context), "%d", n)

    @StringRes
    fun stageText(stage: ConnectStage): Int = when (stage) {
        ConnectStage.PREPARING -> R.string.stage_preparing
        ConnectStage.REGISTERING -> R.string.stage_registering
        ConnectStage.SCANNING -> R.string.stage_scanning
        ConnectStage.CONNECTING -> R.string.stage_connecting
    }

    /** "DE" -> regional indicator letters, which phones draw as a flag. */
    fun flag(code: String): String {
        if (code.length != 2 || !code.all { it in 'A'..'Z' || it in 'a'..'z' }) return ""
        return code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    fun country(context: Context, code: String): String {
        if (code.isBlank()) return ""
        if (code.equals("T1", true)) return "Tor"
        val name = Locale("", code.uppercase()).getDisplayCountry(locale(context))
        return name.ifBlank { code }
    }
}
