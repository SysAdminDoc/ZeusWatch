package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.util.withRetry
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

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
    private val adapters: Map<WeatherSourceProvider, @JvmSuppressWildcards WeatherSourceAdapter>,
    private val timeZoneResolver: LocationTimeZoneResolver,
    private val providerHealthRepository: ProviderHealthRepository,
) {

    // ── Forecast ────────────────────────────────────────────────────────

    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationTimeZone: String? = null,
        sourceOverrides: SourceOverrides = SourceOverrides(),
    ): Result<WeatherData> {
        val config = prefs.settings.first().sourceConfig.withOverrides(sourceOverrides)
        val primary = config.forecast
        val fallback = config.forecastFallback

        val result = withRetry {
            getForecastFrom(primary, latitude, longitude, locationName, locationTimeZone)
        }
        if (result.isSuccess) {
            providerHealthRepository.recordSuccess(
                type = WeatherDataType.FORECAST,
                provider = primary,
                cacheAgeMinutes = result.getOrNull()?.cacheAgeMinutes(),
            )
            return result.map { it.copy(sourceProvider = primary.displayName) }
        }

        providerHealthRepository.recordFailure(
            type = WeatherDataType.FORECAST,
            provider = primary,
            exception = result.exceptionOrNull(),
            clearActiveFallback = fallback == null || fallback == primary,
        )
        Log.w(TAG, "Primary forecast source ${primary.displayName} failed, trying fallback", result.exceptionOrNull())

        if (fallback != null && fallback != primary) {
            val fallbackResult = withRetry {
                getForecastFrom(fallback, latitude, longitude, locationName, locationTimeZone)
            }
            if (fallbackResult.isSuccess) {
                providerHealthRepository.recordSuccess(
                    type = WeatherDataType.FORECAST,
                    provider = fallback,
                    cacheAgeMinutes = fallbackResult.getOrNull()?.cacheAgeMinutes(),
                    activeFallback = true,
                    fallbackFromProvider = primary,
                )
                return fallbackResult.map {
                    it.copy(sourceProvider = fallback.displayName, usedFallback = true)
                }
            }
            providerHealthRepository.recordFailure(
                type = WeatherDataType.FORECAST,
                provider = fallback,
                exception = fallbackResult.exceptionOrNull(),
                clearActiveFallback = true,
            )
            Log.w(TAG, "Fallback forecast source ${fallback.displayName} also failed", fallbackResult.exceptionOrNull())
        }

        return result
    }

    suspend fun getWeatherFromProvider(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationTimeZone: String? = null,
    ): Result<WeatherData> = withRetry {
        getForecastFrom(provider, latitude, longitude, locationName, locationTimeZone)
    }.map { it.copy(sourceProvider = provider.displayName) }

    private suspend fun getForecastFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
        locationName: String?,
        locationTimeZone: String?,
    ): Result<WeatherData> {
        val adapter = adapterFor(provider, WeatherDataType.FORECAST)
            ?: return unsupportedProvider(provider, WeatherDataType.FORECAST)

        val forecastZone = if (adapter.requiresForecastZone) {
            resolveForecastZone(latitude, longitude, locationTimeZone)
        } else {
            null
        }

        return adapter.getForecast(
            ForecastSourceRequest(
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                forecastZone = forecastZone,
            ),
        ).map { it.withUniqueHourlyTimes() }
    }

    private suspend fun resolveForecastZone(
        latitude: Double,
        longitude: Double,
        locationTimeZone: String?,
    ): ZoneId = timeZoneResolver.resolveZone(latitude, longitude, locationTimeZone)

    // ── Alerts ──────────────────────────────────────────────────────────

    suspend fun getAlerts(
        latitude: Double,
        longitude: Double,
        sourceOverrides: SourceOverrides = SourceOverrides(),
        includeMeteredSources: Boolean = true,
        countryHint: String? = null,
    ): Result<List<WeatherAlert>> {
        val config = prefs.settings.first().sourceConfig.withOverrides(sourceOverrides)
        val primary = config.alerts
        val fallback = config.alertsFallback

        val result = withRetry {
            getAlertsFrom(primary, latitude, longitude, includeMeteredSources, countryHint)
        }
        if (result.isSuccess) {
            providerHealthRepository.recordSuccess(
                type = WeatherDataType.ALERTS,
                provider = primary,
                cacheAgeMinutes = null,
            )
            return result
        }

        providerHealthRepository.recordFailure(
            type = WeatherDataType.ALERTS,
            provider = primary,
            exception = result.exceptionOrNull(),
            clearActiveFallback = fallback == null || fallback == primary,
        )
        Log.w(TAG, "Primary alert source ${primary.displayName} failed, trying fallback", result.exceptionOrNull())

        if (fallback != null && fallback != primary) {
            val fallbackResult = withRetry {
                getAlertsFrom(fallback, latitude, longitude, includeMeteredSources, countryHint)
            }
            if (fallbackResult.isSuccess) {
                providerHealthRepository.recordSuccess(
                    type = WeatherDataType.ALERTS,
                    provider = fallback,
                    cacheAgeMinutes = null,
                    activeFallback = true,
                    fallbackFromProvider = primary,
                )
                return fallbackResult
            }
            providerHealthRepository.recordFailure(
                type = WeatherDataType.ALERTS,
                provider = fallback,
                exception = fallbackResult.exceptionOrNull(),
                clearActiveFallback = true,
            )
            Log.w(TAG, "Fallback alert source ${fallback.displayName} also failed", fallbackResult.exceptionOrNull())
        }

        return result
    }

    /**
     * Detailed variant of [getAlerts] that preserves the outage signal end to
     * end (see [AlertRepository.getAlertsDetailed]). Tries the primary alert
     * source; only falls back when the primary was a total failure so a real
     * "no alerts" answer isn't second-guessed.
     */
    suspend fun getAlertsDetailed(
        latitude: Double,
        longitude: Double,
        sourceOverrides: SourceOverrides = SourceOverrides(),
        includeMeteredSources: Boolean = true,
        countryHint: String? = null,
    ): AlertFetchResult {
        val config = prefs.settings.first().sourceConfig.withOverrides(sourceOverrides)
        val primary = config.alerts
        val fallback = config.alertsFallback

        val primaryResult = getAlertsDetailedFrom(primary, latitude, longitude, includeMeteredSources, countryHint)
        if (!primaryResult.allAdaptersFailed) {
            providerHealthRepository.recordSuccess(
                type = WeatherDataType.ALERTS,
                provider = primary,
                cacheAgeMinutes = null,
            )
            return primaryResult
        }

        providerHealthRepository.recordFailure(
            type = WeatherDataType.ALERTS,
            provider = primary,
            reason = ProviderFailureReason.UNKNOWN,
            clearActiveFallback = fallback == null || fallback == primary,
        )
        Log.w(TAG, "Primary alert source ${primary.displayName} failed, trying fallback")

        if (fallback != null && fallback != primary) {
            val fallbackResult = getAlertsDetailedFrom(
                fallback,
                latitude,
                longitude,
                includeMeteredSources,
                countryHint,
            )
            if (!fallbackResult.allAdaptersFailed) {
                providerHealthRepository.recordSuccess(
                    type = WeatherDataType.ALERTS,
                    provider = fallback,
                    cacheAgeMinutes = null,
                    activeFallback = true,
                    fallbackFromProvider = primary,
                )
                return fallbackResult
            }
            providerHealthRepository.recordFailure(
                type = WeatherDataType.ALERTS,
                provider = fallback,
                reason = ProviderFailureReason.UNKNOWN,
                clearActiveFallback = true,
            )
            Log.w(TAG, "Fallback alert source ${fallback.displayName} also failed")
        }

        return primaryResult
    }

    private suspend fun getAlertsDetailedFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
        includeMeteredSources: Boolean,
        countryHint: String?,
    ): AlertFetchResult {
        val adapter = adapterFor(provider, WeatherDataType.ALERTS)
            ?: return unsupportedAlertFetch(provider)

        return adapter.getAlertsDetailed(
            AlertSourceRequest(
                latitude = latitude,
                longitude = longitude,
                includeMeteredSources = includeMeteredSources,
                countryHint = countryHint,
            ),
        )
    }

    private suspend fun getAlertsFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
        includeMeteredSources: Boolean,
        countryHint: String?,
    ): Result<List<WeatherAlert>> {
        val adapter = adapterFor(provider, WeatherDataType.ALERTS)
            ?: return unsupportedProvider(provider, WeatherDataType.ALERTS)

        return adapter.getAlerts(
            AlertSourceRequest(
                latitude = latitude,
                longitude = longitude,
                includeMeteredSources = includeMeteredSources,
                countryHint = countryHint,
            ),
        )
    }

    // ── Air Quality ─────────────────────────────────────────────────────

    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
    ): Result<AirQualityData> {
        val config = prefs.settings.first().sourceConfig
        val primary = config.airQuality

        val result = withRetry { getAirQualityFrom(primary, latitude, longitude) }
        recordHealthResult(WeatherDataType.AIR_QUALITY, primary, result)
        return result
    }

    private suspend fun getAirQualityFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
    ): Result<AirQualityData> {
        val adapter = adapterFor(provider, WeatherDataType.AIR_QUALITY)
            ?: return unsupportedProvider(provider, WeatherDataType.AIR_QUALITY)

        return adapter.getAirQuality(CoordinateSourceRequest(latitude, longitude))
    }

    // ── Minutely ────────────────────────────────────────────────────────

    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> {
        val config = prefs.settings.first().sourceConfig
        val primary = config.minutely

        val result = withRetry { getMinutelyFrom(primary, latitude, longitude) }
        recordHealthResult(WeatherDataType.MINUTELY, primary, result)
        return result
    }

    private suspend fun getMinutelyFrom(
        provider: WeatherSourceProvider,
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> {
        val adapter = adapterFor(provider, WeatherDataType.MINUTELY)
            ?: return unsupportedProvider(provider, WeatherDataType.MINUTELY)

        return adapter.getMinutely(CoordinateSourceRequest(latitude, longitude))
            .map { it.withUniqueAscendingTimes { entry -> entry.time } }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun adapterFor(
        provider: WeatherSourceProvider,
        type: WeatherDataType,
    ): WeatherSourceAdapter? = adapters[provider]?.takeIf { type in it.supportedTypes }

    private fun <T> unsupportedProvider(
        provider: WeatherSourceProvider,
        type: WeatherDataType,
    ): Result<T> = Result.failure(UnsupportedOperationException("${provider.displayName} does not support ${type.label}"))

    private fun unsupportedAlertFetch(provider: WeatherSourceProvider): AlertFetchResult =
        AlertFetchResult(
            alerts = emptyList(),
            allAdaptersFailed = true,
            failedSources = listOf(provider.name),
        )

    private suspend fun <T> recordHealthResult(
        type: WeatherDataType,
        provider: WeatherSourceProvider,
        result: Result<T>,
    ) {
        if (result.isSuccess) {
            providerHealthRepository.recordSuccess(
                type = type,
                provider = provider,
                cacheAgeMinutes = null,
            )
        } else {
            providerHealthRepository.recordFailure(
                type = type,
                provider = provider,
                exception = result.exceptionOrNull(),
                clearActiveFallback = true,
            )
        }
    }

    private val WeatherDataType.label: String
        get() = when (this) {
            WeatherDataType.FORECAST -> "forecasts"
            WeatherDataType.ALERTS -> "alerts"
            WeatherDataType.AIR_QUALITY -> "air quality"
            WeatherDataType.MINUTELY -> "minutely data"
        }
}

private fun WeatherData.cacheAgeMinutes(): Long =
    Duration.between(lastUpdated, java.time.LocalDateTime.now())
        .toMinutes()
        .coerceAtLeast(0L)

/**
 * Normalize the hourly series to unique, ascending timestamps.
 *
 * Adapters that project UTC instants into a location-local [LocalDateTime]
 * (MET Norway, Bright Sky, Pirate Weather, …) emit the repeated hour twice
 * with an identical wall-clock time on the 25-hour DST fall-back day. The
 * hourly UI keys lazy items by `item.time`, so a duplicate crashes Compose
 * with "Key was already used". Keep the FIRST occurrence of a duplicated
 * timestamp. Applied on every live fetch (here) and on cache reads
 * (WeatherRepository) so pre-fix cached payloads are normalized too.
 */
internal fun WeatherData.withUniqueHourlyTimes(): WeatherData {
    val normalized = hourly.withUniqueAscendingTimes { it.time }
    return if (normalized === hourly) this else copy(hourly = normalized)
}

/**
 * Returns this list unchanged when its [time] keys are already strictly
 * ascending; otherwise returns a stably sorted copy with the first
 * occurrence of each duplicated timestamp retained.
 */
internal inline fun <T> List<T>.withUniqueAscendingTimes(
    crossinline time: (T) -> LocalDateTime,
): List<T> {
    val alreadyNormalized = (1 until size).all { time(this[it]) > time(this[it - 1]) }
    if (alreadyNormalized) return this
    val seen = HashSet<LocalDateTime>(size * 2)
    // sortedBy is stable, so equal timestamps keep their original order and
    // the first occurrence survives the dedup below.
    return sortedBy { time(it) }.filter { seen.add(time(it)) }
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
 * Adapter for Open-Meteo's documented BOM ACCESS-G model proxy.
 */
@Singleton
class OpenMeteoBomForecastAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = weatherRepository.get().getBomWeatherDirect(latitude, longitude, locationName)
}

@Singleton
class OpenMeteoKmaForecastAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = weatherRepository.get().getKmaWeatherDirect(latitude, longitude, locationName)
}

@Singleton
class OpenMeteoUkmoForecastAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = weatherRepository.get().getUkmoWeatherDirect(latitude, longitude, locationName)
}

@Singleton
class OpenMeteoDmiForecastAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = weatherRepository.get().getDmiWeatherDirect(latitude, longitude, locationName)
}

@Singleton
class OpenMeteoMeteoFranceForecastAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = weatherRepository.get().getMeteoFranceWeatherDirect(latitude, longitude, locationName)
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

@Singleton
class OpenMeteoMeteoFranceMinutelyAdapter @Inject constructor(
    private val weatherRepository: dagger.Lazy<WeatherRepository>,
) {
    suspend fun getMinutelyPrecipitation(
        latitude: Double,
        longitude: Double,
    ): Result<List<MinutelyPrecipitation>> =
        weatherRepository.get().getMeteoFranceMinutelyPrecipitationDirect(latitude, longitude)
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
        preferenceOverride: AlertSourcePreference? = null,
        includeMeteredSources: Boolean = true,
        countryHint: String? = null,
    ): Result<List<WeatherAlert>> = alertRepository.getAlerts(
        latitude = latitude,
        longitude = longitude,
        preferenceOverride = preferenceOverride,
        includeMeteredSources = includeMeteredSources,
        countryHint = countryHint,
    )

    suspend fun getAlertsDetailed(
        latitude: Double,
        longitude: Double,
        preferenceOverride: AlertSourcePreference? = null,
        includeMeteredSources: Boolean = true,
        countryHint: String? = null,
    ): AlertFetchResult = alertRepository.getAlertsDetailed(
        latitude = latitude,
        longitude = longitude,
        preferenceOverride = preferenceOverride,
        includeMeteredSources = includeMeteredSources,
        countryHint = countryHint,
    )
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
