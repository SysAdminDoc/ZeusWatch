package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WeatherSourceManager"

/**
 * Manages multi-source weather data fetching with automatic fallback.
 *
 * Each data type (forecast, alerts, AQI, minutely) can be configured with a
 * primary source and optional fallback. If the primary fails, the fallback
 * is attempted transparently.
 */
@Singleton
class WeatherSourceManager @Inject constructor(
    private val prefs: UserPreferences,
    private val openMeteoAdapter: OpenMeteoForecastAdapter,
    private val openMeteoMinutelyAdapter: OpenMeteoMinutelyAdapter,
    private val nwsAlertAdapter: AlertSourceManagerAdapter,
    private val openMeteoAqiAdapter: OpenMeteoAqiAdapter,
) {

    // ── Forecast ────────────────────────────────────────────────────────

    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> {
        val config = prefs.settings.first().sourceConfig
        val primary = config.forecast
        val fallback = config.forecastFallback

        val result = getForecastFrom(primary, latitude, longitude, locationName)
        if (result.isSuccess) return result

        Log.w(TAG, "Primary forecast source ${primary.displayName} failed, trying fallback", result.exceptionOrNull())

        if (fallback != null && fallback != primary) {
            val fallbackResult = getForecastFrom(fallback, latitude, longitude, locationName)
            if (fallbackResult.isSuccess) return fallbackResult
            Log.w(TAG, "Fallback forecast source ${fallback.displayName} also failed", fallbackResult.exceptionOrNull())
        }

        return result // Return original failure
    }

    private suspend fun getForecastFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
        locationName: String?,
    ): Result<WeatherData> = when (provider) {
        WeatherSourceProvider.OPEN_METEO -> openMeteoAdapter.getWeather(latitude, longitude, locationName)
        WeatherSourceProvider.OPEN_WEATHER_MAP -> stubResult("OpenWeatherMap forecast")
        WeatherSourceProvider.PIRATE_WEATHER -> stubResult("Pirate Weather forecast")
        WeatherSourceProvider.BRIGHT_SKY -> stubResult("Bright Sky forecast")
        WeatherSourceProvider.ENVIRONMENT_CANADA -> stubResult("Environment Canada forecast")
        else -> Result.failure(UnsupportedOperationException("${provider.displayName} does not support forecasts"))
    }

    // ── Alerts ──────────────────────────────────────────────────────────

    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> {
        val config = prefs.settings.first().sourceConfig
        val primary = config.alerts
        val fallback = config.alertsFallback

        val result = getAlertsFrom(primary, latitude, longitude)
        if (result.isSuccess) return result

        Log.w(TAG, "Primary alert source ${primary.displayName} failed, trying fallback", result.exceptionOrNull())

        if (fallback != null && fallback != primary) {
            val fallbackResult = getAlertsFrom(fallback, latitude, longitude)
            if (fallbackResult.isSuccess) return fallbackResult
            Log.w(TAG, "Fallback alert source ${fallback.displayName} also failed", fallbackResult.exceptionOrNull())
        }

        return result
    }

    private suspend fun getAlertsFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> = when (provider) {
        WeatherSourceProvider.NWS -> nwsAlertAdapter.getAlerts(latitude, longitude)
        WeatherSourceProvider.METEOALARM -> nwsAlertAdapter.getAlerts(latitude, longitude)
        WeatherSourceProvider.JMA -> nwsAlertAdapter.getAlerts(latitude, longitude)
        WeatherSourceProvider.OPEN_WEATHER_MAP -> stubResult("OpenWeatherMap alerts")
        WeatherSourceProvider.BRIGHT_SKY -> stubResult("Bright Sky alerts")
        WeatherSourceProvider.ENVIRONMENT_CANADA -> stubResult("Environment Canada alerts")
        else -> Result.failure(UnsupportedOperationException("${provider.displayName} does not support alerts"))
    }

    // ── Air Quality ─────────────────────────────────────────────────────

    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
    ): Result<AirQualityData> {
        val config = prefs.settings.first().sourceConfig
        val primary = config.airQuality

        return getAirQualityFrom(primary, latitude, longitude)
    }

    private suspend fun getAirQualityFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
    ): Result<AirQualityData> = when (provider) {
        WeatherSourceProvider.OPEN_METEO -> openMeteoAqiAdapter.getAirQuality(latitude, longitude)
        WeatherSourceProvider.OPEN_WEATHER_MAP -> stubResult("OpenWeatherMap AQI")
        else -> Result.failure(UnsupportedOperationException("${provider.displayName} does not support air quality"))
    }

    // ── Minutely ────────────────────────────────────────────────────────

    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> {
        val config = prefs.settings.first().sourceConfig
        val primary = config.minutely

        return getMinutelyFrom(primary, latitude, longitude)
    }

    private suspend fun getMinutelyFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> = when (provider) {
        WeatherSourceProvider.OPEN_METEO -> openMeteoMinutelyAdapter.getMinutelyPrecipitation(latitude, longitude)
        else -> Result.failure(UnsupportedOperationException("${provider.displayName} does not support minutely data"))
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun <T> stubResult(name: String): Result<T> =
        Result.failure(NotImplementedError("$name adapter coming soon"))
}

// ── Source Adapters ─────────────────────────────────────────────────────

/**
 * Adapter wrapping the existing Open-Meteo forecast logic in WeatherRepository.
 * This keeps WeatherRepository's mapping code intact while enabling the source manager.
 */
@Singleton
class OpenMeteoForecastAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = weatherRepository.get().getWeatherDirect(latitude, longitude, locationName)
}

/**
 * Adapter wrapping the existing Open-Meteo minutely precipitation logic.
 */
@Singleton
class OpenMeteoMinutelyAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> = weatherRepository.get().getMinutelyPrecipitationDirect(latitude, longitude)
}

/**
 * Adapter wrapping the unified AlertRepository for the multi-source fallback system.
 * Delegates to AlertRepository which now handles international source selection internally.
 */
@Singleton
class AlertSourceManagerAdapter @Inject constructor(
    private val alertRepository: AlertRepository,
) {
    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
    ): Result<List<WeatherAlert>> = alertRepository.getAlerts(latitude, longitude)
}

/**
 * Adapter wrapping the existing Open-Meteo air quality logic.
 */
@Singleton
class OpenMeteoAqiAdapter @Inject constructor(
    private val airQualityRepository: AirQualityRepository,
) {
    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
    ): Result<AirQualityData> = airQualityRepository.getAirQuality(latitude, longitude)
}
