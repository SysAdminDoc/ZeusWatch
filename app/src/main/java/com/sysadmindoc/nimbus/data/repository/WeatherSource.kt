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
    private val implementedTypes: Set<WeatherDataType> = supportedTypes,
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
    ),
    PIRATE_WEATHER(
        displayName = "Pirate Weather",
        supportedTypes = setOf(WeatherDataType.FORECAST),
        requiresApiKey = true,
    ),
    BRIGHT_SKY(
        displayName = "Bright Sky (DWD)",
        supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS),
    ),
    ENVIRONMENT_CANADA(
        displayName = "Environment Canada",
        supportedTypes = setOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS),
        implementedTypes = setOf(WeatherDataType.ALERTS),
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

    /** Returns true if the app currently implements this provider for the given type. */
    fun isImplementedFor(type: WeatherDataType): Boolean = type in implementedTypes

    /** Returns true when the provider is both valid and implemented for the requested type. */
    fun isSelectableFor(type: WeatherDataType): Boolean = supports(type) && isImplementedFor(type)

    companion object {
        /** Returns all implemented providers that support a given data type. */
        fun forType(type: WeatherDataType): List<WeatherSourceProvider> =
            entries.filter { it.isSelectableFor(type) }

        /** Returns the safe built-in default source for a given data type. */
        fun defaultFor(type: WeatherDataType): WeatherSourceProvider = when (type) {
            WeatherDataType.FORECAST,
            WeatherDataType.AIR_QUALITY,
            WeatherDataType.MINUTELY -> OPEN_METEO
            WeatherDataType.ALERTS -> NWS
        }
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
) {
    /**
     * Coerces stale or unsupported stored provider selections back to implemented values.
     * This prevents older installs from getting stuck on sources the current build hides.
     */
    fun normalized(): SourceConfig {
        val normalizedForecast = forecast.normalizedPrimary(WeatherDataType.FORECAST)
        val normalizedAlerts = alerts.normalizedPrimary(WeatherDataType.ALERTS)
        val normalizedAirQuality = airQuality.normalizedPrimary(WeatherDataType.AIR_QUALITY)
        val normalizedMinutely = minutely.normalizedPrimary(WeatherDataType.MINUTELY)

        return copy(
            forecast = normalizedForecast,
            forecastFallback = forecastFallback.normalizedFallback(
                type = WeatherDataType.FORECAST,
                primary = normalizedForecast,
            ),
            alerts = normalizedAlerts,
            alertsFallback = alertsFallback.normalizedFallback(
                type = WeatherDataType.ALERTS,
                primary = normalizedAlerts,
            ),
            airQuality = normalizedAirQuality,
            minutely = normalizedMinutely,
        )
    }
}

private fun WeatherSourceProvider.normalizedPrimary(type: WeatherDataType): WeatherSourceProvider =
    takeIf { it.isSelectableFor(type) } ?: WeatherSourceProvider.defaultFor(type)

private fun WeatherSourceProvider?.normalizedFallback(
    type: WeatherDataType,
    primary: WeatherSourceProvider,
): WeatherSourceProvider? = this
    ?.takeIf { it.isSelectableFor(type) }
    ?.takeUnless { it == primary }
