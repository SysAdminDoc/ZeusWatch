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
import java.time.ZoneId
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
    private val httpCache: MetNorwayHttpCache,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
        locationZone: ZoneId? = null,
    ): Result<WeatherData> = runCatching {
        val zone = locationZone ?: ZoneId.systemDefault()
        val cached = httpCache.get(latitude, longitude)

        if (cached != null && cached.isFresh()) {
            return@runCatching mapToWeatherData(cached.response, latitude, longitude, locationName, zone)
        }

        val httpResponse = api.getForecast(
            latitude, longitude,
            ifModifiedSince = cached?.lastModified,
        )

        val metResponse = when (httpResponse.code()) {
            304 -> {
                cached?.response ?: throw IllegalStateException("304 but no cached response")
            }
            in 200..299 -> {
                val body = httpResponse.body() ?: throw IllegalStateException("Empty 200 response from MET Norway")
                val lastMod = httpResponse.headers()["Last-Modified"]
                val expires = MetNorwayHttpCache.parseHttpDate(httpResponse.headers()["Expires"])
                httpCache.put(latitude, longitude, MetNorwayHttpCache.CacheEntry(
                    lastModified = lastMod,
                    expires = expires,
                    response = body,
                ))
                body
            }
            else -> throw retrofit2.HttpException(httpResponse)
        }

        mapToWeatherData(metResponse, latitude, longitude, locationName, zone)
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w(TAG, "MET Norway forecast failed", it)
    }

    /**
     * MET Norway always emits UTC timestamps (the response carries no
     * timezone metadata), so we resolve them into a concrete [ZoneId] —
     * the device's current zone by default — before exposing them to the
     * UI. Without this, hourly labels and the "today" daily lookup are
     * off by the user's UTC offset, which is visible whenever the offset
     * isn't zero (i.e., for nearly every MET Norway user).
     */
    internal fun mapToWeatherData(
        response: MetNorwayResponse,
        lat: Double,
        lon: Double,
        locationName: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeatherData {
        val timeseries = response.properties?.timeseries.orEmpty()
        require(timeseries.isNotEmpty()) { "No timeseries data from MET Norway" }

        val currentEntry = findCurrentEntry(timeseries) ?: timeseries.first()
        val currentInstant = currentEntry.data?.instant?.details
        val currentLocalTime = parseZonedLocalDateTime(currentEntry.time, zone)
            ?: LocalDateTime.now(zone)
        val today = currentLocalTime.toLocalDate()

        val hourly = mapHourly(timeseries, currentLocalTime, zone)
        val daily = aggregateDaily(timeseries, zone)
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
                timeZone = zone.id,
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
        zone: ZoneId,
    ): List<HourlyConditions> {
        return timeseries.mapNotNull { entry ->
            val time = parseZonedLocalDateTime(entry.time, zone) ?: return@mapNotNull null
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
        zone: ZoneId,
    ): List<DailyConditions> {
        return timeseries.groupBy { entry -> parseZonedLocalDateTime(entry.time, zone)?.toLocalDate() }
            .filterKeys { it != null }
            .map { (date, dayEntries) ->
                val temps = dayEntries.mapNotNull { it.data?.instant?.details?.airTemperature }
                val winds = dayEntries.mapNotNull {
                    it.data?.instant?.details?.windSpeed?.let { v -> v * MS_TO_KMH }
                }
                val gusts = dayEntries.mapNotNull {
                    it.data?.instant?.details?.windSpeedOfGust?.let { v -> v * MS_TO_KMH }
                }
                // MET degrades to 6-hourly periods after ~48h, where entries
                // carry only next_6_hours. Sum next_1_hours where present and
                // fill the gaps with non-overlapping next_6_hours blocks —
                // tracking the covered-until instant prevents double-counting
                // when 1h and 6h windows (or overlapping 6h windows) coexist.
                val precipSum = run {
                    var sum = 0.0
                    var coveredUntil: LocalDateTime? = null
                    val timed = dayEntries
                        .mapNotNull { e -> parseZonedLocalDateTime(e.time, zone)?.let { it to e } }
                        .sortedBy { it.first }
                    for ((time, e) in timed) {
                        if (coveredUntil != null && time.isBefore(coveredUntil)) continue
                        val next1 = e.data?.next1Hours?.details?.precipitationAmount
                        if (next1 != null) {
                            sum += next1
                            coveredUntil = time.plusHours(1)
                        } else {
                            val next6 = e.data?.next6Hours?.details?.precipitationAmount ?: continue
                            sum += next6
                            coveredUntil = time.plusHours(6)
                        }
                    }
                    sum
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

    /**
     * Parse a MET-Norway UTC timestamp and project it into [zone] before
     * dropping the offset. `OffsetDateTime.toLocalDateTime()` directly
     * would discard the offset and treat the UTC clock as if it were the
     * user's local clock, which produces hourly labels off by the user's
     * UTC offset. Going through `atZoneSameInstant` first preserves the
     * instant and renders it in the requested zone.
     */
    private fun parseZonedLocalDateTime(timestamp: String, zone: ZoneId): LocalDateTime? = try {
        OffsetDateTime.parse(timestamp).atZoneSameInstant(zone).toLocalDateTime()
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
