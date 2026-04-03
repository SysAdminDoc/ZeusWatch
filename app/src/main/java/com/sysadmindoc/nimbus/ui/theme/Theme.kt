package com.sysadmindoc.nimbus.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sysadmindoc.nimbus.data.model.WeatherCode

private val NimbusDarkScheme = darkColorScheme(
    primary = NimbusBlueAccent,
    onPrimary = NimbusTextPrimary,
    primaryContainer = NimbusNavyLight,
    onPrimaryContainer = NimbusNavyDark,
    secondary = NimbusRainBlue,
    onSecondary = NimbusNavyDark,
    secondaryContainer = NimbusSurfaceElevated,
    onSecondaryContainer = NimbusTextPrimary,
    tertiary = NimbusSunYellow,
    onTertiary = NimbusNavyDark,
    background = NimbusNavyDark,
    onBackground = NimbusTextPrimary,
    surface = NimbusSurface,
    onSurface = NimbusTextPrimary,
    surfaceVariant = NimbusSurfaceVariant,
    onSurfaceVariant = NimbusTextSecondary,
    outline = NimbusCardBorder,
    outlineVariant = Color(0xFF203A61),
    error = NimbusError,
    onError = NimbusTextPrimary,
    surfaceTint = NimbusBlueAccent,
    scrim = NimbusNavyDark.copy(alpha = 0.72f),
)

/**
 * Weather-adaptive color scheme variants.
 * Shifts the primary/accent colors based on current conditions.
 */
private fun weatherAdaptiveScheme(weatherCode: WeatherCode?, isDay: Boolean): ColorScheme {
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

    return NimbusDarkScheme.copy(
        primary = accentColor,
        tertiary = accentColor.copy(alpha = 0.7f),
    )
}

@Composable
fun NimbusTheme(
    useWeatherAdaptive: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Read weather state from CompositionLocal (provided by MainScreen)
    val weatherState = LocalWeatherThemeState.current
    val colorScheme = if (useWeatherAdaptive && weatherState.weatherCode != null) {
        weatherAdaptiveScheme(weatherState.weatherCode, weatherState.isDay)
    } else {
        NimbusDarkScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NimbusTypography,
        content = content,
    )
}
