package com.sysadmindoc.nimbus.data.repository

import androidx.compose.runtime.Stable

/**
 * Defines the available weather data types and source providers
 * for the multi-source fallback system.
 */

enum class WeatherDataType {
    FORECAST,     // Current + hourly + daily
    ALERTS,       // Severe weather alerts
    AIR_QUALITY,  // AQI + pollen
    MINUTELY,     // Precipitation nowcasting
}

enum class WeatherSourceProvider(
    val displayName: String,
    val supportedTypes: Set<WeatherDataType>,
    val requiresApiKey: Boolean = false,
    val implemented: Boolean = true,
) {
    OPEN_METEO(
        displayName = "Open-Meteo",
        supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.AIR_QUALITY, WeatherDataType.MINUTELY),
    ),
    NWS(
        displayName = "National Weather Service",
        supportedTypes = setOf(WeatherDataType.ALERTS),
    ),
    OPEN_WEATHER_MAP(
        displayName = "OpenWeatherMap",
        supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS, WeatherDataType.AIR_QUALITY),
        requiresApiKey = true,
        implemented = false,
    ),
    PIRATE_WEATHER(
        displayName = "Pirate Weather",
        supportedTypes = setOf(WeatherDataType.FORECAST),
        requiresApiKey = true,
        implemented = false,
    ),
    BRIGHT_SKY(
        displayName = "Bright Sky (DWD)",
        supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS),
        implemented = false,
    ),
    ENVIRONMENT_CANADA(
        displayName = "Environment Canada",
        supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS),
    ),
    METEOALARM(
        displayName = "MeteoAlarm (EUMETNET)",
        supportedTypes = setOf(WeatherDataType.ALERTS),
    ),
    JMA(
        displayName = "Japan Meteorological Agency",
        supportedTypes = setOf(WeatherDataType.ALERTS),
    );

    /** Returns true if this provider supports the given data type. */
    fun supports(type: WeatherDataType): Boolean = type in supportedTypes

    companion object {
        /** Returns all implemented providers that support a given data type. */
        fun forType(type: WeatherDataType): List<WeatherSourceProvider> =
            entries.filter { it.supports(type) && it.implemented }
    }
}

@Stable
data class SourceConfig(
    val forecast: WeatherSourceProvider = WeatherSourceProvider.OPEN_METEO,
    val forecastFallback: WeatherSourceProvider? = null,
    val alerts: WeatherSourceProvider = WeatherSourceProvider.NWS,
    val alertsFallback: WeatherSourceProvider? = null,
    val airQuality: WeatherSourceProvider = WeatherSourceProvider.OPEN_METEO,
    val minutely: WeatherSourceProvider = WeatherSourceProvider.OPEN_METEO,
)
