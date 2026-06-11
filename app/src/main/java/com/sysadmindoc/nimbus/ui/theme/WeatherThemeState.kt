package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.sysadmindoc.nimbus.data.model.WeatherCode
import kotlinx.coroutines.flow.MutableStateFlow

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

/**
 * App-scoped bridge for weather-adaptive theming. NimbusTheme is applied at the
 * activity root, ABOVE MainScreen, so a CompositionLocal provided inside
 * MainScreen can never reach it. MainScreen pushes the current conditions here;
 * MainActivity collects the flow and provides [LocalWeatherThemeState] above
 * [NimbusTheme] so the WEATHER_ADAPTIVE color scheme actually sees weather data.
 */
object WeatherThemeBus {
    val state = MutableStateFlow(WeatherThemeState())
}
