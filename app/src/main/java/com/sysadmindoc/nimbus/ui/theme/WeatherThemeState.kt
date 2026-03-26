package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.sysadmindoc.nimbus.data.model.WeatherCode

/**
 * Shared state for weather-adaptive theming.
 * MainScreen updates this when weather data loads;
 * NimbusTheme reads it for color scheme selection.
 */
data class WeatherThemeState(
    val weatherCode: WeatherCode? = null,
    val isDay: Boolean = true,
)

val LocalWeatherThemeState = compositionLocalOf { WeatherThemeState() }
