package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.MetNorwayApi
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.MetNorwayResponse
import com.sysadmindoc.nimbus.data.model.MetTimeseriesEntry
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "MetNorwayAdapter"

/**
 * Forecast adapter for MET Norway LocationForecast 2.0.
 *
 * No API key, CC BY 4.0 (attribution surfaced in README + About screen).
 * Global coverage with highest fidelity in the Nordic region — the
 * differentiator for ZeusWatch's Scandinavian users vs. Open-Meteo.
 *
 * Units: MET returns SI — wind in m/s, pressure in hPa, temp in °C. We
 * convert wind to km/h to match the rest of the app. Daily is aggregated
 * from the hourly series since MET doesn't publish daily summaries
 * separately.
 */
@Singleton
class MetNorwayForecastAdapter @Inject constructor(
    private val api: MetNorwayApi,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = runCatching {
        val response = api.getForecast(latitude, longitude)
        mapToWeatherData(response, latitude, longitude, locationName)
    }.onFailure { Log.w(TAG, "MET Norway forecast failed", it) }

    internal fun mapToWeatherData(
        response: MetNorwayResponse,
        lat: Double,
        lon: Double,
        locationName: String?,
    ): WeatherData {
        val timeseries = response.properties?.timeseries.orEmpty()
        require(timeseries.isNotEmpty()) { "No timeseries data from MET Norway" }

        val currentEntry = findCurrentEntry(timeseries) ?: timeseries.first()
        val currentInstant = currentEntry.data?.instant?.details
        val currentLocalTime = parseLocalDateTime(currentEntry.time) ?: LocalDateTime.now()
        val today = currentLocalTime.toLocalDate()

        val hourly = mapHourly(timeseries, currentLocalTime)
        val daily = aggregateDaily(timeseries)
        val todayDaily = daily.firstOrNull { it.date == today }

        // Current weather code uses the `next_1_hours` summary if present,
        // otherwise falls back to cloud cover based classification.
        val currentCode = currentEntry.data?.next1Hours?.summary?.symbolCode
            ?.let { MetSymbolMapper.toWmoCode(it) }
            ?: MetSymbolMapper.codeFromCloudCover(currentInstant?.cloudAreaFraction)

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: "Unknown",
                latitude = lat,
                longitude = lon,
            ),
            current = CurrentConditions(
                temperature = currentInstant?.airTemperature ?: 0.0,
                feelsLike = currentInstant?.airTemperature ?: 0.0, // MET doesn't publish feels-like
                humidity = currentInstant?.relativeHumidity?.toInt() ?: 0,
                weatherCode = WeatherCode.fromCode(currentCode),
                observationTime = currentLocalTime,
                isDay = MetSymbolMapper.isDayFromSymbol(
                    currentEntry.data?.next1Hours?.summary?.symbolCode,
                    currentLocalTime,
                ),
                windSpeed = (currentInstant?.windSpeed ?: 0.0) * MS_TO_KMH,
                windDirection = currentInstant?.windFromDirection?.toInt() ?: 0,
                windGusts = currentInstant?.windSpeedOfGust?.let { it * MS_TO_KMH },
                pressure = currentInstant?.airPressureAtSeaLevel ?: 0.0,
                uvIndex = currentInstant?.ultravioletIndexClearSky ?: 0.0,
                visibility = null, // not provided by MET /complete
                dewPoint = currentInstant?.dewPointTemperature,
                cloudCover = currentInstant?.cloudAreaFraction?.toInt() ?: 0,
                precipitation = currentEntry.data?.next1Hours?.details?.precipitationAmount ?: 0.0,
                dailyHigh = todayDaily?.temperatureHigh ?: currentInstant?.airTemperature ?: 0.0,
                dailyLow = todayDaily?.temperatureLow ?: currentInstant?.airTemperature ?: 0.0,
                sunrise = null, // Sunrise is a separate endpoint; app fetches astronomy elsewhere
                sunset = null,
            ),
            hourly = hourly,
            daily = daily,
        )
    }

    private fun findCurrentEntry(timeseries: List<MetTimeseriesEntry>): MetTimeseriesEntry? {
        val now = Instant.now()
        return timeseries.minByOrNull { entry ->
            val instant = parseInstant(entry.time) ?: return@minByOrNull Long.MAX_VALUE
            abs(Duration.between(now, instant).toMinutes())
        }
    }

    private fun mapHourly(
        timeseries: List<MetTimeseriesEntry>,
        now: LocalDateTime,
    ): List<HourlyConditions> {
        return timeseries.mapNotNull { entry ->
            val time = parseLocalDateTime(entry.time) ?: return@mapNotNull null
            if (time.isBefore(now.minusHours(1))) return@mapNotNull null
            val details = entry.data?.instant?.details
            val next1 = entry.data?.next1Hours
            val symbolCode = next1?.summary?.symbolCode
                ?: entry.data?.next6Hours?.summary?.symbolCode
            val code = symbolCode?.let { MetSymbolMapper.toWmoCode(it) }
                ?: MetSymbolMapper.codeFromCloudCover(details?.cloudAreaFraction)
            HourlyConditions(
                time = time,
                temperature = details?.airTemperature ?: 0.0,
                feelsLike = null,
                weatherCode = WeatherCode.fromCode(code),
                isDay = MetSymbolMapper.isDayFromSymbol(symbolCode, time),
                precipitationProbability = (next1?.details?.probabilityOfPrecipitation
                    ?: entry.data?.next6Hours?.details?.probabilityOfPrecipitation
                    ?: 0.0).toInt(),
                precipitation = next1?.details?.precipitationAmount,
                windSpeed = details?.windSpeed?.let { it * MS_TO_KMH },
                windDirection = details?.windFromDirection?.toInt(),
                humidity = details?.relativeHumidity?.toInt(),
                uvIndex = details?.ultravioletIndexClearSky,
                cloudCover = details?.cloudAreaFraction?.toInt(),
                visibility = null,
                windGusts = details?.windSpeedOfGust?.let { it * MS_TO_KMH },
                surfacePressure = details?.airPressureAtSeaLevel,
            )
        }
    }

    /**
     * Aggregate timeseries entries into daily summaries. MET doesn't
     * publish its own daily block, so we group by local date and take
     * extrema across the hourly values.
     */
    private fun aggregateDaily(
        timeseries: List<MetTimeseriesEntry>,
    ): List<DailyConditions> {
        return timeseries.groupBy { entry -> parseLocalDateTime(entry.time)?.toLocalDate() }
            .filterKeys { it != null }
            .map { (date, dayEntries) ->
                val temps = dayEntries.mapNotNull { it.data?.instant?.details?.airTemperature }
                val winds = dayEntries.mapNotNull {
                    it.data?.instant?.details?.windSpeed?.let { v -> v * MS_TO_KMH }
                }
                val gusts = dayEntries.mapNotNull {
                    it.data?.instant?.details?.windSpeedOfGust?.let { v -> v * MS_TO_KMH }
                }
                val precipSum = dayEntries.sumOf {
                    it.data?.next1Hours?.details?.precipitationAmount ?: 0.0
                }
                val precipProb = dayEntries.mapNotNull {
                    it.data?.next1Hours?.details?.probabilityOfPrecipitation
                        ?: it.data?.next6Hours?.details?.probabilityOfPrecipitation
                }
                val uvMax = dayEntries.mapNotNull {
                    it.data?.instant?.details?.ultravioletIndexClearSky
                }.maxOrNull()
                val windDirs = dayEntries.mapNotNull { it.data?.instant?.details?.windFromDirection }

                val dominantWmo = dayEntries
                    .mapNotNull {
                        val sym = it.data?.next1Hours?.summary?.symbolCode
                            ?: it.data?.next6Hours?.summary?.symbolCode
                        sym?.let { s -> MetSymbolMapper.toWmoCode(s) }
                    }
                    .filter { it > 0 }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }?.key ?: 0

                DailyConditions(
                    date = date!!,
                    weatherCode = WeatherCode.fromCode(dominantWmo),
                    temperatureHigh = temps.maxOrNull() ?: 0.0,
                    temperatureLow = temps.minOrNull() ?: 0.0,
                    precipitationProbability = precipProb.maxOrNull()?.toInt() ?: 0,
                    precipitationSum = precipSum.takeIf { it > 0.0 },
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = uvMax,
                    windSpeedMax = winds.maxOrNull(),
                    windDirectionDominant = if (windDirs.isNotEmpty()) {
                        val sinSum = windDirs.sumOf { kotlin.math.sin(Math.toRadians(it)) }
                        val cosSum = windDirs.sumOf { kotlin.math.cos(Math.toRadians(it)) }
                        ((Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)) + 360) % 360).toInt()
                    } else null,
                    snowfallSum = null,
                    sunshineDuration = null,
                    windGustsMax = gusts.maxOrNull(),
                )
            }
            .sortedBy { it.date }
    }

    private fun parseLocalDateTime(timestamp: String): LocalDateTime? = try {
        OffsetDateTime.parse(timestamp).toLocalDateTime()
    } catch (_: Exception) {
        null
    }

    private fun parseInstant(timestamp: String): Instant? = try {
        OffsetDateTime.parse(timestamp).toInstant()
    } catch (_: Exception) {
        null
    }

    companion object {
        internal const val MS_TO_KMH = 3.6
    }
}

/**
 * Translates MET Norway `symbol_code` values into WMO weather codes.
 * Reference: <https://api.met.no/weatherapi/weathericon/2.0/documentation>
 *
 * Symbol codes follow `<condition>[_day|_night|_polartwilight]`. Only
 * the condition is needed for WMO; day/night is exposed separately
 * through [isDayFromSymbol].
 */
internal object MetSymbolMapper {

    fun toWmoCode(symbolCode: String): Int {
        val base = symbolCode.substringBefore("_")
        return WMO_BY_CONDITION[base] ?: 0
    }

    fun codeFromCloudCover(cloudPct: Double?): Int = when {
        cloudPct == null -> 0
        cloudPct < 12.5 -> 0  // clear sky
        cloudPct < 37.5 -> 1  // fair
        cloudPct < 75.0 -> 2  // partly cloudy
        else -> 3             // overcast
    }

    fun isDayFromSymbol(symbolCode: String?, fallbackTime: LocalDateTime): Boolean {
        if (symbolCode == null) return fallbackTime.hour in 6..19
        return when {
            symbolCode.endsWith("_day") -> true
            symbolCode.endsWith("_night") -> false
            symbolCode.endsWith("_polartwilight") -> false
            else -> fallbackTime.hour in 6..19
        }
    }

    private val WMO_BY_CONDITION: Map<String, Int> = mapOf(
        "clearsky" to 0,
        "fair" to 1,
        "partlycloudy" to 2,
        "cloudy" to 3,
        "fog" to 45,
        "lightrainshowers" to 80,
        "rainshowers" to 81,
        "heavyrainshowers" to 82,
        "lightrainshowersandthunder" to 95,
        "rainshowersandthunder" to 95,
        "heavyrainshowersandthunder" to 96,
        "lightrain" to 61,
        "rain" to 63,
        "heavyrain" to 65,
        "lightrainandthunder" to 95,
        "rainandthunder" to 95,
        "heavyrainandthunder" to 96,
        "lightsleet" to 66,
        "sleet" to 67,
        "heavysleet" to 67,
        "lightsleetandthunder" to 96,
        "sleetandthunder" to 96,
        "heavysleetandthunder" to 99,
        "lightsleetshowers" to 66,
        "sleetshowers" to 67,
        "heavysleetshowers" to 67,
        "lightsleetshowersandthunder" to 96,
        "sleetshowersandthunder" to 96,
        "heavysleetshowersandthunder" to 99,
        "lightsnow" to 71,
        "snow" to 73,
        "heavysnow" to 75,
        "lightsnowandthunder" to 96,
        "snowandthunder" to 96,
        "heavysnowandthunder" to 99,
        "lightsnowshowers" to 85,
        "snowshowers" to 85,
        "heavysnowshowers" to 86,
        "lightsnowshowersandthunder" to 96,
        "snowshowersandthunder" to 96,
        "heavysnowshowersandthunder" to 99,
        "lightssleetshowersandthunder" to 96, // MET has a documented typo in some feeds
    )
}
