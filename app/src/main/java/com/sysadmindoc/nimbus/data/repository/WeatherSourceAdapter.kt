package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import java.time.ZoneId

data class ForecastSourceRequest(
    val latitude: Double,
    val longitude: Double,
    val locationName: String?,
    val forecastZone: ZoneId?,
)

data class AlertSourceRequest(
    val latitude: Double,
    val longitude: Double,
    val includeMeteredSources: Boolean,
    val countryHint: String?,
)

data class CoordinateSourceRequest(
    val latitude: Double,
    val longitude: Double,
)

interface WeatherSourceAdapter {
    val provider: WeatherSourceProvider
    val supportedTypes: Set<WeatherDataType>
    val requiresForecastZone: Boolean get() = false

    suspend fun getForecast(request: ForecastSourceRequest): Result<WeatherData> =
        unsupported(WeatherDataType.FORECAST)

    suspend fun getAlerts(request: AlertSourceRequest): Result<List<WeatherAlert>> =
        unsupported(WeatherDataType.ALERTS)

    suspend fun getAlertsDetailed(request: AlertSourceRequest): AlertFetchResult =
        getAlerts(request).toFetchResult(provider.name)

    suspend fun getAirQuality(request: CoordinateSourceRequest): Result<AirQualityData> =
        unsupported(WeatherDataType.AIR_QUALITY)

    suspend fun getMinutely(request: CoordinateSourceRequest): Result<List<MinutelyPrecipitation>> =
        unsupported(WeatherDataType.MINUTELY)

    private fun <T> unsupported(type: WeatherDataType): Result<T> =
        Result.failure(UnsupportedOperationException("${provider.displayName} does not support ${type.label}"))
}

fun Result<List<WeatherAlert>>.toFetchResult(sourceId: String): AlertFetchResult =
    fold(
        onSuccess = { AlertFetchResult(it, allAdaptersFailed = false, failedSources = emptyList()) },
        onFailure = { AlertFetchResult(emptyList(), allAdaptersFailed = true, failedSources = listOf(sourceId)) },
    )

private val WeatherDataType.label: String
    get() = when (this) {
        WeatherDataType.FORECAST -> "forecasts"
        WeatherDataType.ALERTS -> "alerts"
        WeatherDataType.AIR_QUALITY -> "air quality"
        WeatherDataType.MINUTELY -> "minutely data"
    }
