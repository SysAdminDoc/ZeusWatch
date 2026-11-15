package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.sysadmindoc.nimbus.data.model.WeatherCode

// The schemes are mode-static, so they read NimbusPalette values directly —
// NOT the theme-aware composable getters in Color.kt (top-level initializers
// cannot call composables). Values mirror NimbusPalette.Dark 1:1.
private val NimbusDarkScheme = darkColorScheme(
    primary = NimbusBlueAccent,
    onPrimary = Color(0xFF01070D),
    primaryContainer = Color(0xFF173A5C),
    onPrimaryContainer = Color(0xFFF4F7FB),
    secondary = NimbusRainBlue,
    onSecondary = Color(0xFF01070D),
    secondaryContainer = Color(0xFF102538),
    onSecondaryContainer = Color(0xFFF4F7FB),
    tertiary = NimbusSunYellow,
    onTertiary = Color(0xFF01070D),
    background = Color(0xFF01070D),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF07121C),
    onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF0B1A27),
    onSurfaceVariant = Color(0xFFB7C3D4),
    outline = Color(0x6630475B),
    outlineVariant = Color(0xFF203A61),
    error = Color(0xFFCF6679),
    onError = Color(0xFF01070D),
    surfaceTint = NimbusBlueAccent,
    scrim = Color(0xFF01070D).copy(alpha = 0.72f),
)

/**
 * Normal light theme (issue #59): white surfaces on cool paper, with the
 * accent deepened so white ink on filled controls holds WCAG AA.
 */
internal val NimbusLightScheme = lightColorScheme(
    primary = Color(0xFF0A66C2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E5FB),
    onPrimaryContainer = Color(0xFF16202B),
    secondary = Color(0xFF1273C4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E9FB),
    onSecondaryContainer = Color(0xFF16202B),
    tertiary = Color(0xFF9A6A00),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF16202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF44566B),
    outline = Color(0xFF8CA0B4),
    outlineVariant = Color(0xFFC3CFDA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFF0A66C2),
    scrim = Color(0x66000A14),
)

/**
 * Weather-adaptive color scheme variants.
 * Shifts the primary/accent colors based on current conditions.
 */
internal fun weatherAdaptiveScheme(weatherCode: WeatherCode?, isDay: Boolean): ColorScheme {
    if (weatherCode == null) return NimbusDarkScheme

    val accentColor = when {
        // Sunny/clear
        weatherCode in listOf(WeatherCode.CLEAR_SKY, WeatherCode.MAINLY_CLEAR) && isDay ->
            Color(0xFFFFB74D) // Warm amber
        // Clear night
        weatherCode in listOf(WeatherCode.CLEAR_SKY, WeatherCode.MAINLY_CLEAR) && !isDay ->
            Color(0xFF5C6BC0) // Indigo
        // Cloudy
        weatherCode in listOf(WeatherCode.PARTLY_CLOUDY, WeatherCode.OVERCAST) ->
            Color(0xFF78909C) // Blue-grey
        // Rain
        weatherCode in listOf(
            WeatherCode.RAIN_SLIGHT, WeatherCode.RAIN_MODERATE, WeatherCode.RAIN_HEAVY,
            WeatherCode.DRIZZLE_LIGHT, WeatherCode.DRIZZLE_MODERATE, WeatherCode.DRIZZLE_DENSE,
            WeatherCode.RAIN_SHOWERS_SLIGHT, WeatherCode.RAIN_SHOWERS_MODERATE, WeatherCode.RAIN_SHOWERS_VIOLENT,
        ) -> Color(0xFF4FC3F7) // Light blue
        // Snow
        weatherCode in listOf(
            WeatherCode.SNOW_SLIGHT, WeatherCode.SNOW_MODERATE, WeatherCode.SNOW_HEAVY,
            WeatherCode.SNOW_GRAINS, WeatherCode.SNOW_SHOWERS_SLIGHT, WeatherCode.SNOW_SHOWERS_HEAVY,
        ) -> Color(0xFFB3E5FC) // Ice blue
        // Thunderstorm
        weatherCode in listOf(
            WeatherCode.THUNDERSTORM, WeatherCode.THUNDERSTORM_HAIL_SLIGHT, WeatherCode.THUNDERSTORM_HAIL_HEAVY,
        ) -> Color(0xFFCE93D8) // Purple
        // Fog
        weatherCode in listOf(WeatherCode.FOG, WeatherCode.DEPOSITING_RIME_FOG) ->
            Color(0xFFBCAAA4) // Warm grey
        // Freezing
        weatherCode in listOf(
            WeatherCode.FREEZING_DRIZZLE_LIGHT, WeatherCode.FREEZING_DRIZZLE_DENSE,
            WeatherCode.FREEZING_RAIN_LIGHT, WeatherCode.FREEZING_RAIN_HEAVY,
        ) -> Color(0xFF80DEEA) // Cyan
        else -> NimbusBlueAccent
    }

    val tertiaryColor = accentColor
    return NimbusDarkScheme.copy(
        primary = accentColor,
        onPrimary = accentOnColor(accentColor),
        tertiary = tertiaryColor,
        onTertiary = accentOnColor(tertiaryColor),
    )
}

/**
 * Picks the text color (deep navy or near-white) with the higher WCAG contrast
 * ratio against [accent], so accent containers never render near-invisible
 * text regardless of how light or dark the weather-adaptive accent is.
 */
internal fun accentOnColor(accent: Color): Color {
    val accentLuminance = accent.luminance()
    val darkTextContrast = (accentLuminance + 0.05f) / (NimbusRawInkDark.luminance() + 0.05f)
    val lightTextContrast = (NimbusRawInkLight.luminance() + 0.05f) / (accentLuminance + 0.05f)
    return if (darkTextContrast >= lightTextContrast) NimbusRawInkDark else NimbusRawInkLight
}

@Composable
fun NimbusTheme(
    useWeatherAdaptive: Boolean = false,
    isLightTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Read weather state from CompositionLocal (provided by MainActivity, fed
    // from MainScreen via WeatherThemeBus)
    val weatherState = LocalWeatherThemeState.current
    val colorScheme = when {
        isLightTheme -> NimbusLightScheme
        useWeatherAdaptive && weatherState.weatherCode != null ->
            weatherAdaptiveScheme(weatherState.weatherCode, weatherState.isDay)
        else -> NimbusDarkScheme
    }
    val palette = if (isLightTheme) NimbusPalette.Light else NimbusPalette.Dark

    CompositionLocalProvider(LocalNimbusPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NimbusTypography,
            content = content,
        )
    }
}
