package com.sysadmindoc.nimbus.data.repository

import android.util.Log
import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.model.*
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PwAdapter"

/**
 * Convert a Pirate Weather 0..1 fraction to a 0..100 percent integer,
 * guarding against NaN and out-of-range API values. Dark-Sky-compatible
 * payloads have been observed to occasionally emit values slightly above
 * 1.0 or negative values under erroneous conditions.
 */
private fun pctToInt(fraction: Double): Int {
    if (fraction.isNaN()) return 0
    return (fraction * 100).toInt().coerceIn(0, 100)
}

/**
 * Forecast adapter for Pirate Weather (Dark Sky-compatible API).
 * Maps PW response to [WeatherData] domain model.
 * SI units: Celsius, m/s, hPa, mm/h. Wind → km/h for consistency.
 * Visibility arrives in km (SI).
 */
@Singleton
class PirateWeatherForecastAdapter @Inject constructor(
    private val api: PirateWeatherApi,
    private val prefs: UserPreferences,
) {
    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): Result<WeatherData> = runCatching {
        val apiKey = prefs.settings.first().pirateWeatherApiKey
        require(apiKey.isNotBlank()) { "Pirate Weather API key not configured" }

        val response = api.getForecast(apiKey, latitude, longitude)
        mapToWeatherData(response, latitude, longitude, locationName)
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w(TAG, "Pirate Weather forecast failed", it)
    }

    private fun mapToWeatherData(
        r: PirateWeatherResponse,
        lat: Double,
        lon: Double,
        locationName: String?,
    ): WeatherData {
        val current = r.currently ?: error("No current data from Pirate Weather")
        val zone = try { ZoneId.of(r.timezone) } catch (_: Exception) { ZoneId.systemDefault() }
        val dailyData = r.daily?.data ?: emptyList()
        val firstDaily = dailyData.firstOrNull()
        val locationLocalNow = epochToLocalDateTime(current.time, zone)

        return WeatherData(
            location = LocationInfo(
                name = locationName ?: "Unknown",
                latitude = lat,
                longitude = lon,
                timeZone = r.timezone.takeIf { it.toZoneIdOrNull() != null },
            ),
            current = CurrentConditions(
                temperature = current.temperature,
                feelsLike = current.apparentTemperature,
                humidity = pctToInt(current.humidity),
                weatherCode = WeatherCode.fromCode(
                    PwIconMapper.toWmoCode(current.icon, current.precipType)
                ),
                observationTime = locationLocalNow,
                isDay = PwIconMapper.isDayFromIcon(
                    icon = current.icon,
                    timeEpochSeconds = current.time,
                    sunriseEpochSeconds = firstDaily?.sunriseTime,
                    sunsetEpochSeconds = firstDaily?.sunsetTime,
                    fallbackHour = locationLocalNow.hour,
                ),
                windSpeed = (current.windSpeed * 3.6).coerceAtLeast(0.0), // m/s → km/h
                windDirection = current.windBearing,
                windGusts = current.windGust?.let { (it * 3.6).coerceAtLeast(0.0) },
                pressure = current.pressure,
                uvIndex = current.uvIndex.coerceAtLeast(0.0),
                visibility = current.visibility?.let { (it * 1000.0).coerceAtLeast(0.0) }, // km → meters (WeatherFormatter expects meters)
                dewPoint = current.dewPoint,
                cloudCover = pctToInt(current.cloudCover),
                precipitation = current.precipIntensity.coerceAtLeast(0.0),
                // precipIntensity is mm/h liquid-equivalent; formatter contract is cm
                snowfall = if (current.precipType == "snow") (current.precipIntensity / 10.0).coerceAtLeast(0.0) else null,
                dailyHigh = firstDaily?.temperatureHigh ?: current.temperature,
                dailyLow = firstDaily?.temperatureLow ?: current.temperature,
                sunrise = firstDaily?.sunriseTime?.let { epochToTimeStr(it, zone) },
                sunset = firstDaily?.sunsetTime?.let { epochToTimeStr(it, zone) },
            ),
            hourly = mapHourly(r.hourly?.data ?: emptyList(), dailyData, zone, locationLocalNow),
            daily = mapDaily(dailyData, zone),
        )
    }

    private fun mapHourly(
        data: List<PwHourly>,
        dailyData: List<PwDaily>,
        zone: ZoneId,
        now: LocalDateTime,
    ): List<HourlyConditions> {
        // Daily sunrise/sunset epochs keyed by local date so ambiguous-icon
        // hours (cloudy/rain/snow/fog carry no day/night suffix) can compute
        // day-ness from the actual sun window instead of defaulting to day.
        val dailyByDate = dailyData.associateBy { epochToLocalDate(it.time, zone) }
        return data.mapNotNull { h ->
            val time = epochToLocalDateTime(h.time, zone)
            if (time.isBefore(now.minusHours(1))) return@mapNotNull null
            val dayEntry = dailyByDate[time.toLocalDate()]
            HourlyConditions(
                time = time,
                temperature = h.temperature,
                feelsLike = h.apparentTemperature,
                weatherCode = WeatherCode.fromCode(
                    PwIconMapper.toWmoCode(h.icon, h.precipType)
                ),
                isDay = PwIconMapper.isDayFromIcon(
                    icon = h.icon,
                    timeEpochSeconds = h.time,
                    sunriseEpochSeconds = dayEntry?.sunriseTime,
                    sunsetEpochSeconds = dayEntry?.sunsetTime,
                    fallbackHour = time.hour,
                ),
                precipitationProbability = pctToInt(h.precipProbability ?: 0.0),
                precipitation = h.precipIntensity?.coerceAtLeast(0.0),
                windSpeed = h.windSpeed?.let { (it * 3.6).coerceAtLeast(0.0) },
                windDirection = h.windBearing,
                humidity = h.humidity?.let { pctToInt(it) },
                uvIndex = h.uvIndex?.coerceAtLeast(0.0),
                cloudCover = h.cloudCover?.let { pctToInt(it) },
                visibility = h.visibility?.let { (it * 1000.0).coerceAtLeast(0.0) }, // km → meters
                // precipIntensity is mm/h liquid-equivalent; formatter contract is cm
                snowfall = if (h.precipType == "snow") h.precipIntensity?.let { (it / 10.0).coerceAtLeast(0.0) } else null,
                windGusts = h.windGust?.let { (it * 3.6).coerceAtLeast(0.0) },
                surfacePressure = h.pressure,
            )
        }
    }

    private fun mapDaily(data: List<PwDaily>, zone: ZoneId): List<DailyConditions> {
        return data.map { d ->
            DailyConditions(
                date = epochToLocalDate(d.time, zone),
                weatherCode = WeatherCode.fromCode(
                    PwIconMapper.toWmoCode(d.icon, d.precipType)
                ),
                temperatureHigh = d.temperatureHigh,
                temperatureLow = d.temperatureLow,
                precipitationProbability = pctToInt(d.precipProbability ?: 0.0),
                precipitationSum = dailyPrecipitationSum(d),
                sunrise = d.sunriseTime?.let { epochToTimeStr(it, zone) },
                sunset = d.sunsetTime?.let { epochToTimeStr(it, zone) },
                uvIndexMax = d.uvIndex?.coerceAtLeast(0.0),
                windSpeedMax = d.windSpeed?.let { (it * 3.6).coerceAtLeast(0.0) },
                windDirectionDominant = d.windBearing,
                snowfallSum = dailySnowfallSum(d),
                windGustsMax = d.windGust?.let { (it * 3.6).coerceAtLeast(0.0) },
            )
        }
    }

    /**
     * Daily liquid-equivalent precipitation in mm (domain contract).
     * Pirate Weather daily blocks carry `liquidAccumulation` in centimeters
     * (SI units), which is the API's real daily total — the legacy
     * avg-intensity × 24 estimate overstates totals whenever precipitation
     * doesn't fall all 24 hours. Keep the estimate only as a fallback for
     * payloads without accumulation fields.
     */
    private fun dailyPrecipitationSum(d: PwDaily): Double? =
        d.liquidAccumulation?.let { (it * 10.0).coerceAtLeast(0.0) } // cm → mm
            ?: d.precipIntensity?.let { (it * 24).coerceAtLeast(0.0) } // mm/h → approximate mm/day

    /**
     * Daily snowfall in cm (formatter contract). `snowAccumulation`
     * (Pirate Weather) and `precipAccumulation` (Dark Sky, snowfall only)
     * are already snow-depth centimeters — never re-apply the 10:1
     * liquid-to-snow ratio to them. When an accumulation field is present it
     * is authoritative (0 → no snow); the mm/h intensity estimate remains
     * only as a fallback for accumulation-less payloads.
     */
    private fun dailySnowfallSum(d: PwDaily): Double? {
        d.snowAccumulation?.let { return it.coerceAtLeast(0.0).takeIf { cm -> cm > 0.0 } }
        if (d.precipType != "snow") return null
        d.precipAccumulation?.let { return it.coerceAtLeast(0.0) }
        // mm/h liquid-equivalent × 24 → mm/day, then ÷10 → cm (formatter contract)
        return d.precipIntensity?.let { (it * 24 / 10.0).coerceAtLeast(0.0) }
    }

    private fun epochToLocalDateTime(epoch: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochSecond(epoch).atZone(zone).toLocalDateTime()

    private fun epochToLocalDate(epoch: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()

    private fun epochToTimeStr(epoch: Long, zone: ZoneId): String =
        Instant.ofEpochSecond(epoch).atZone(zone).toLocalDateTime()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
