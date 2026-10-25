package com.samaliyan.nimbus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.samaliyan.nimbus.R
import com.samaliyan.nimbus.core.ThemeMode

/** Colours that are not part of Material's scheme: the sky of the connect orb and the states. */
@Immutable
data class NimbusColors(
    val skyTop: Color,
    val skyBottom: Color,
    val cloudTop: Color,
    val cloudBottom: Color,
    val muted: Color,
    val warn: Color,
    val error: Color,
    val ok: Color,
    val hairline: Color,
    val dark: Boolean,
)

private val LightNimbus = NimbusColors(
    skyTop = Color(0xFF4F7CFF),
    skyBottom = Color(0xFF7FD6FF),
    cloudTop = Color(0xFFFFFFFF),
    cloudBottom = Color(0xFFDCE3EE),
    muted = Color(0xFF5E6B85),
    warn = Color(0xFFC77C02),
    error = Color(0xFFD93A3F),
    ok = Color(0xFF2C7BE5),
    hairline = Color(0xFFD9E0EA),
    dark = false,
)

private val DarkNimbus = NimbusColors(
    skyTop = Color(0xFF5B86FF),
    skyBottom = Color(0xFF6FD0FF),
    cloudTop = Color(0xFF2A3854),
    cloudBottom = Color(0xFF172236),
    muted = Color(0xFF8D9AB3),
    warn = Color(0xFFF5B142),
    error = Color(0xFFFF6B6F),
    ok = Color(0xFF7FB2FF),
    hairline = Color(0xFF26324A),
    dark = true,
)

val LocalNimbus = staticCompositionLocalOf { LightNimbus }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F6AE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF0E2A78),
    secondary = Color(0xFF516079),
    background = Color(0xFFEEF2F7),
    onBackground = Color(0xFF17213A),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF17213A),
    surfaceVariant = Color(0xFFE3E9F2),
    onSurfaceVariant = Color(0xFF5E6B85),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F4F9),
    surfaceContainerHighest = Color(0xFFE8EDF4),
    surfaceContainerLow = Color(0xFFF6F8FB),
    outline = Color(0xFFC3CCDA),
    outlineVariant = Color(0xFFD9E0EA),
    error = Color(0xFFD93A3F),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AAEFF),
    onPrimary = Color(0xFF0B1C4D),
    primaryContainer = Color(0xFF243A73),
    onPrimaryContainer = Color(0xFFDDE6FF),
    secondary = Color(0xFFAAB6CC),
    background = Color(0xFF0D1524),
    onBackground = Color(0xFFE6ECF6),
    surface = Color(0xFF0D1524),
    onSurface = Color(0xFFE6ECF6),
    surfaceVariant = Color(0xFF1B2638),
    onSurfaceVariant = Color(0xFF8D9AB3),
    surfaceContainer = Color(0xFF151F31),
    surfaceContainerHigh = Color(0xFF1B2639),
    surfaceContainerHighest = Color(0xFF222E44),
    surfaceContainerLow = Color(0xFF111A2B),
    outline = Color(0xFF3A475F),
    outlineVariant = Color(0xFF26324A),
    error = Color(0xFFFF6B6F),
)

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazirmatn)
    return Typography(
        displayLarge = base.displayLarge.v(),
        displayMedium = base.displayMedium.v(),
        displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v(),
        headlineMedium = base.headlineMedium.v().copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 36.sp),
        headlineSmall = base.headlineSmall.v().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.v().copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.v().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.v().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.v().copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.v().copy(lineHeight = 22.sp),
        bodySmall = base.bodySmall.v().copy(lineHeight = 19.sp),
        labelLarge = base.labelLarge.v().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.v(),
        labelSmall = base.labelSmall.v(),
    )
}

private val NimbusTypography = typography()

@Composable
fun NimbusTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalNimbus provides if (dark) DarkNimbus else LightNimbus) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = NimbusTypography,
            content = content,
        )
    }
}
