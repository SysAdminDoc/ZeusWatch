package com.sysadmindoc.nimbus.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.toZoneIdOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val GADGETBRIDGE_WEATHER_ACTION =
    "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
internal const val GADGETBRIDGE_WEATHER_JSON_EXTRA = "WeatherJson"
internal const val GADGETBRIDGE_WEATHER_SECONDARY_JSON_EXTRA = "WeatherSecondaryJson"
internal const val GADGETBRIDGE_WEATHER_GZ_EXTRA = "WeatherGz"
internal const val GADGETBRIDGE_MAX_LOCATIONS = 3

private const val TAG = "GadgetbridgeWeather"
private const val UNKNOWN_OPEN_WEATHER_CODE = 3200

private val gadgetbridgeJson = Json {
    encodeDefaults = true
}

private val openWeatherConditionCodes = mapOf(
    WeatherCode.CLEAR_SKY to 800,
    WeatherCode.MAINLY_CLEAR to 801,
    WeatherCode.PARTLY_CLOUDY to 802,
    WeatherCode.OVERCAST to 804,
    WeatherCode.FOG to 741,
    WeatherCode.DEPOSITING_RIME_FOG to 741,
    WeatherCode.DRIZZLE_LIGHT to 300,
    WeatherCode.DRIZZLE_MODERATE to 301,
    WeatherCode.DRIZZLE_DENSE to 302,
    WeatherCode.FREEZING_DRIZZLE_LIGHT to 511,
    WeatherCode.FREEZING_DRIZZLE_DENSE to 511,
    WeatherCode.RAIN_SLIGHT to 500,
    WeatherCode.RAIN_MODERATE to 501,
    WeatherCode.RAIN_HEAVY to 502,
    WeatherCode.FREEZING_RAIN_LIGHT to 511,
    WeatherCode.FREEZING_RAIN_HEAVY to 511,
    WeatherCode.SNOW_SLIGHT to 600,
    WeatherCode.SNOW_MODERATE to 601,
    WeatherCode.SNOW_HEAVY to 602,
    WeatherCode.SNOW_GRAINS to 611,
    WeatherCode.RAIN_SHOWERS_SLIGHT to 520,
    WeatherCode.RAIN_SHOWERS_MODERATE to 521,
    WeatherCode.RAIN_SHOWERS_VIOLENT to 522,
    WeatherCode.SNOW_SHOWERS_SLIGHT to 620,
    WeatherCode.SNOW_SHOWERS_HEAVY to 622,
    WeatherCode.THUNDERSTORM to 200,
    WeatherCode.THUNDERSTORM_HAIL_SLIGHT to 201,
    WeatherCode.THUNDERSTORM_HAIL_HEAVY to 202,
    WeatherCode.UNKNOWN to UNKNOWN_OPEN_WEATHER_CODE,
)

@Singleton
class GadgetbridgeWeatherBroadcaster @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun broadcast(
        primary: WeatherData,
        secondary: List<WeatherData> = emptyList(),
    ) {
        val components = queryWeatherReceivers()
        if (components.isEmpty()) return

        val payloads = buildGadgetbridgeWeatherPayloads(
            primary = primary,
            secondary = secondary,
            conditionLabel = { code -> code.localizedDescription(context) },
        )
        components.forEach { component ->
            sendWeatherBroadcast(component, payloads)
        }
    }

    private fun queryWeatherReceivers(): List<ComponentName> {
        val intent = Intent(GADGETBRIDGE_WEATHER_ACTION)
        return runCatching {
            val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryBroadcastReceivers(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryBroadcastReceivers(
                    intent,
                    PackageManager.GET_RESOLVED_FILTER,
                )
            }
            receivers.mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                ComponentName(activityInfo.packageName, activityInfo.name)
            }.distinct()
        }.getOrElse { error ->
            Log.w(TAG, "Could not discover Gadgetbridge weather receivers", error)
            emptyList()
        }
    }

    private fun sendWeatherBroadcast(
        component: ComponentName,
        payloads: GadgetbridgeWeatherPayloads,
    ) {
        val intent = Intent(GADGETBRIDGE_WEATHER_ACTION)
            .setComponent(component)
            .putExtra(GADGETBRIDGE_WEATHER_JSON_EXTRA, payloads.primaryJson)
            .putExtra(GADGETBRIDGE_WEATHER_SECONDARY_JSON_EXTRA, payloads.secondaryJson)
            .putExtra(GADGETBRIDGE_WEATHER_GZ_EXTRA, payloads.weatherGz)
        runCatching { context.sendBroadcast(intent) }
            .onFailure { error ->
                Log.w(TAG, "Could not broadcast weather to ${component.flattenToShortString()}", error)
            }
    }
}

internal data class GadgetbridgeWeatherPayloads(
    val primaryJson: String,
    val secondaryJson: String,
    val weatherGz: ByteArray,
)

internal fun buildGadgetbridgeWeatherPayloads(
    primary: WeatherData,
    secondary: List<WeatherData> = emptyList(),
    conditionLabel: (WeatherCode) -> String = { it.description },
): GadgetbridgeWeatherPayloads {
    val specs = (listOf(primary) + secondary)
        .take(GADGETBRIDGE_MAX_LOCATIONS)
        .map { weather -> weather.toGadgetbridgeWeatherSpec(conditionLabel) }
    val allLocationsJson = gadgetbridgeJson.encodeToString(specs)
    return GadgetbridgeWeatherPayloads(
        primaryJson = gadgetbridgeJson.encodeToString(specs.first()),
        secondaryJson = gadgetbridgeJson.encodeToString(specs.drop(1)),
        weatherGz = gzipUtf8(allLocationsJson),
    )
}

private fun WeatherData.toGadgetbridgeWeatherSpec(
    conditionLabel: (WeatherCode) -> String,
): GadgetbridgeWeatherSpec {
    val zone = location.timeZone.toZoneIdOrNull() ?: ZoneId.systemDefault()
    val today = current.observationTime?.toLocalDate()
        ?: daily.firstOrNull()?.date
        ?: LocalDate.now(zone)
    val todayForecast = daily.firstOrNull { it.date == today }
    return GadgetbridgeWeatherSpec(
        timestamp = (current.observationTime ?: lastUpdated).toEpochSeconds(zone),
        location = location.name,
        currentTemp = current.temperature.toKelvinInt(),
        currentConditionCode = current.weatherCode.toOpenWeatherCode(),
        currentCondition = current.sourceConditionText.takeUnlessBlank() ?: conditionLabel(current.weatherCode),
        currentHumidity = current.humidity,
        todayMaxTemp = current.dailyHigh.toKelvinInt(),
        todayMinTemp = current.dailyLow.toKelvinInt(),
        windSpeed = current.windSpeed.toFloat(),
        windDirection = current.windDirection,
        uvIndex = current.uvIndex.toFloat(),
        precipProbability = currentPrecipProbability(todayForecast),
        dewPoint = current.dewPoint?.toKelvinInt() ?: 0,
        pressure = current.pressure.toFloat(),
        cloudCover = current.cloudCover,
        visibility = current.visibility?.toFloat() ?: 0f,
        sunRise = dateTimeStringToEpochSeconds(current.sunrise, today, zone),
        sunSet = dateTimeStringToEpochSeconds(current.sunset, today, zone),
        latitude = location.latitude.toCoarseCoordinate(),
        longitude = location.longitude.toCoarseCoordinate(),
        feelsLikeTemp = current.feelsLike.toKelvinInt(),
        forecasts = daily
            .filter { it.date.isAfter(today) }
            .take(7)
            .map { it.toGadgetbridgeDailySpec(zone) },
        hourly = hourly
            .take(24)
            .map { it.toGadgetbridgeHourlySpec(zone) },
    )
}

private fun WeatherData.currentPrecipProbability(todayForecast: DailyConditions?): Int =
    hourly.firstOrNull()?.precipitationProbability
        ?: todayForecast?.precipitationProbability
        ?: 0

private fun DailyConditions.toGadgetbridgeDailySpec(zone: ZoneId): GadgetbridgeDailySpec =
    GadgetbridgeDailySpec(
        maxTemp = temperatureHigh.toKelvinInt(),
        minTemp = temperatureLow.toKelvinInt(),
        conditionCode = weatherCode.toOpenWeatherCode(),
        windSpeed = windSpeedMax?.toFloat() ?: 0f,
        windDirection = windDirectionDominant ?: 0,
        uvIndex = uvIndexMax?.toFloat() ?: 0f,
        precipProbability = precipitationProbability,
        sunRise = dateTimeStringToEpochSeconds(sunrise, date, zone),
        sunSet = dateTimeStringToEpochSeconds(sunset, date, zone),
    )

private fun HourlyConditions.toGadgetbridgeHourlySpec(zone: ZoneId): GadgetbridgeHourlySpec =
    GadgetbridgeHourlySpec(
        timestamp = time.toEpochSeconds(zone),
        temp = temperature.toKelvinInt(),
        conditionCode = weatherCode.toOpenWeatherCode(),
        humidity = humidity ?: 0,
        windSpeed = windSpeed?.toFloat() ?: 0f,
        windDirection = windDirection ?: 0,
        uvIndex = uvIndex?.toFloat() ?: 0f,
        precipProbability = precipitationProbability,
    )

private fun WeatherCode.toOpenWeatherCode(): Int =
    openWeatherConditionCodes[this] ?: UNKNOWN_OPEN_WEATHER_CODE

private fun String?.takeUnlessBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun dateTimeStringToEpochSeconds(
    raw: String?,
    date: LocalDate,
    zone: ZoneId,
): Int {
    if (raw.isNullOrBlank()) return 0
    return runCatching {
        LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.recoverCatching {
        LocalTime.parse(raw, DateTimeFormatter.ISO_LOCAL_TIME).atDate(date)
    }.map { it.toEpochSeconds(zone) }.getOrDefault(0)
}

private fun LocalDateTime.toEpochSeconds(zone: ZoneId): Int =
    atZone(zone).toEpochSecond().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

private fun Double.toKelvinInt(): Int =
    (this + 273.15).roundToInt().coerceAtLeast(0)

// The Gadgetbridge action is a public broadcast any installed app can resolve,
// so round coordinates to 2 decimals (~1.1 km — the community-report
// precision) instead of leaking a full-precision location fix.
private fun Double.toCoarseCoordinate(): Float =
    ((this * 100.0).roundToInt() / 100.0).toFloat()

private fun gzipUtf8(value: String): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { gzip ->
        gzip.write(value.toByteArray(Charsets.UTF_8))
    }
    return output.toByteArray()
}

@Serializable
internal data class GadgetbridgeWeatherSpec(
    val timestamp: Int,
    val location: String,
    val currentTemp: Int,
    val currentConditionCode: Int,
    val currentCondition: String,
    val currentHumidity: Int,
    val todayMaxTemp: Int,
    val todayMinTemp: Int,
    val windSpeed: Float,
    val windDirection: Int,
    val uvIndex: Float,
    val precipProbability: Int,
    val dewPoint: Int,
    val pressure: Float,
    val cloudCover: Int,
    val visibility: Float,
    val sunRise: Int = 0,
    val sunSet: Int = 0,
    val moonRise: Int = 0,
    val moonSet: Int = 0,
    val moonPhase: Int = 0,
    val latitude: Float,
    val longitude: Float,
    val feelsLikeTemp: Int,
    val isCurrentLocation: Int = -1,
    val forecasts: List<GadgetbridgeDailySpec> = emptyList(),
    val hourly: List<GadgetbridgeHourlySpec> = emptyList(),
)

@Serializable
internal data class GadgetbridgeDailySpec(
    val maxTemp: Int,
    val minTemp: Int,
    val conditionCode: Int,
    val humidity: Int = 0,
    val windSpeed: Float = 0f,
    val windDirection: Int = 0,
    val uvIndex: Float = 0f,
    val precipProbability: Int = 0,
    val sunRise: Int = 0,
    val sunSet: Int = 0,
    val moonRise: Int = 0,
    val moonSet: Int = 0,
    val moonPhase: Int = 0,
)

@Serializable
internal data class GadgetbridgeHourlySpec(
    val timestamp: Int,
    val temp: Int,
    val conditionCode: Int,
    val humidity: Int = 0,
    val windSpeed: Float = 0f,
    val windDirection: Int = 0,
    val uvIndex: Float = 0f,
    val precipProbability: Int = 0,
)
