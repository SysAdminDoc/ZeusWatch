package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.AirQualityApi
import com.sysadmindoc.nimbus.data.api.AqHourly
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.AstronomyData
import com.sysadmindoc.nimbus.data.model.DailyAqi
import com.sysadmindoc.nimbus.data.model.HourlyAqi
import com.sysadmindoc.nimbus.data.model.MoonPhase
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenReading
import com.sysadmindoc.nimbus.data.model.PollenThresholdsDb
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.roundToInt

@Singleton
class AirQualityRepository @Inject constructor(
    private val api: AirQualityApi,
) {
    suspend fun getAirQuality(lat: Double, lon: Double): Result<AirQualityData> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getAirQuality(lat, lon)
                val current = response.current
                    ?: return@withContext Result.failure(Exception("No air quality data"))

                val usAqi = current.usAqi ?: 0
                val euAqi = current.europeanAqi ?: 0

                val pollenFromCurrent = PollenData(
                    alder = PollenReading.fromConcentration(current.alderPollen, "Alder", PollenThresholdsDb.ALDER),
                    birch = PollenReading.fromConcentration(current.birchPollen, "Birch", PollenThresholdsDb.BIRCH),
                    grass = PollenReading.fromConcentration(current.grassPollen, "Grass", PollenThresholdsDb.GRASS),
                    mugwort = PollenReading.fromConcentration(current.mugwortPollen, "Mugwort", PollenThresholdsDb.MUGWORT),
                    olive = PollenReading.fromConcentration(current.olivePollen, "Olive", PollenThresholdsDb.OLIVE),
                    ragweed = PollenReading.fromConcentration(current.ragweedPollen, "Ragweed", PollenThresholdsDb.RAGWEED),
                    moldSpores = PollenReading.fromConcentration(current.mouldSpores, "Mold", PollenThresholdsDb.MOLD_SPORES),
                )

                // Build hourly AQI (next 24h). The API is called with `timezone=auto`,
                // so all timestamps in `hourly.time` and `current.time` are in the
                // LOCATION's local timezone — not the device's. We anchor "now" off
                // `current.time` instead of `LocalDateTime.now()`, otherwise viewing a
                // distant location (e.g. Denver phone looking at Tokyo weather) would
                // filter the entire hourly list out because device-now is hours ahead
                // of or behind location-now.
                val now = parseApiLocalTime(current.time) ?: LocalDateTime.now()

                // Pollen: fall back to hourly data if current returns no pollen
                val pollen = if (!pollenFromCurrent.hasData && response.hourly != null) {
                    extractCurrentHourPollen(response.hourly, now)
                } else {
                    pollenFromCurrent
                }
                val hourlyAqi = mutableListOf<HourlyAqi>()
                // Also collect daily max AQI by grouping hourly data by date
                val dailyAqiMap = mutableMapOf<java.time.LocalDate, Int>()
                response.hourly?.let { h ->
                    for (i in h.time.indices) {
                        val t = try {
                            LocalDateTime.parse(h.time[i], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        } catch (_: Exception) { continue }

                        val aqi = h.usAqi?.getOrNull(i) ?: continue

                        // Hourly AQI (next 24h)
                        if (!t.isBefore(now.minusHours(1)) && hourlyAqi.size < 24) {
                            hourlyAqi.add(HourlyAqi(
                                time = t,
                                aqi = aqi,
                                level = AqiLevel.fromAqi(aqi),
                            ))
                        }

                        // Daily max aggregation (all available days)
                        val date = t.toLocalDate()
                        dailyAqiMap[date] = maxOf(dailyAqiMap[date] ?: 0, aqi)
                    }
                }

                // Build daily AQI forecast from today onwards (up to 5 days)
                val today = now.toLocalDate()
                val dailyAqi = dailyAqiMap
                    .filter { it.key >= today }
                    .toSortedMap()
                    .entries
                    .take(5)
                    .map { (date, maxAqi) ->
                        DailyAqi(
                            dayLabel = WeatherFormatter.formatRelativeDayLabel(date, today),
                            maxAqi = maxAqi,
                            level = AqiLevel.fromAqi(maxAqi),
                        )
                    }

                Result.success(AirQualityData(
                    usAqi = usAqi,
                    europeanAqi = euAqi,
                    aqiLevel = AqiLevel.fromAqi(usAqi),
                    pm25 = current.pm25 ?: 0.0,
                    pm10 = current.pm10 ?: 0.0,
                    ozone = current.ozone ?: 0.0,
                    nitrogenDioxide = current.nitrogenDioxide ?: 0.0,
                    sulphurDioxide = current.sulphurDioxide ?: 0.0,
                    carbonMonoxide = current.carbonMonoxide ?: 0.0,
                    pollen = pollen,
                    hourlyAqi = hourlyAqi,
                    dailyAqi = dailyAqi,
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Calculate astronomy data from date and observer location.
     * Moon position uses Meeus-style geocentric model in [MoonAstronomy];
     * moonrise/moonset use hourly altitude sampling with linear interpolation
     * on the observer's local-time horizon crossings.
     */
    fun getAstronomy(
        sunrise: String?,
        sunset: String?,
        latitude: Double,
        longitude: Double,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        referenceTime: LocalDateTime? = null,
    ): AstronomyData {
        val now = referenceTime
            ?: parseApiLocalTime(sunrise)
            ?: parseApiLocalTime(sunset)
            ?: LocalDateTime.now()
        val nowZoned = now.atZone(zoneId)
        val illumination = com.sysadmindoc.nimbus.util.MoonAstronomy.illuminationPercent(nowZoned)
        val lunarAge = com.sysadmindoc.nimbus.util.MoonAstronomy.lunarAgeDays(nowZoned)
        val phase = MoonPhase.fromDayOfCycle(lunarAge)

        // True moonrise/moonset for the observer's location + date.
        val times = com.sysadmindoc.nimbus.util.MoonAstronomy.riseSetForDate(
            date = now.toLocalDate(),
            latDeg = latitude,
            lonDeg = longitude,
            zone = zoneId,
        )
        // riseSetForDate returns the rise and set that occur ON this calendar date;
        // the moon commonly sets in the morning and rises again that evening, so a
        // set time earlier than the rise time is still the SAME day's set — do not
        // roll it to the next day (that mislabeled moonset ~24h late).
        val moonrise = times.rise?.let { LocalDateTime.of(now.toLocalDate(), it).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        val moonset = times.set?.let { setTime ->
            LocalDateTime.of(now.toLocalDate(), setTime).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }

        // Day length from sunrise/sunset
        val dayLength = calculateDayLength(sunrise, sunset)

        return AstronomyData(
            moonPhase = phase,
            moonIllumination = illumination,
            moonrise = moonrise,
            moonset = moonset,
            dayLength = dayLength,
            observerLatitude = latitude,
        )
    }

    private fun calculateDayLength(sunrise: String?, sunset: String?): String? {
        if (sunrise == null || sunset == null) return null
        return try {
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val rise = LocalDateTime.parse(sunrise, fmt)
            val set = LocalDateTime.parse(sunset, fmt)
            val minutes = ChronoUnit.MINUTES.between(rise, set)
            if (minutes < 0) return null
            String.format(Locale.US, "%dh %dm", minutes / 60, minutes % 60)
        } catch (_: Exception) { null }
    }

    /**
     * Parse an Open-Meteo "current.time" string into a LocalDateTime in the
     * location's timezone (the API returns "YYYY-MM-DDTHH:mm" when
     * `timezone=auto` is used). Returns null on parse failure so the caller can
     * fall back to `LocalDateTime.now()`.
     */
    private fun parseApiLocalTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract pollen readings from hourly data for the current hour.
     * Open-Meteo may return null pollen in `current` but provide it in `hourly`.
     */
    private fun extractCurrentHourPollen(hourly: AqHourly, now: LocalDateTime): PollenData {
        val currentIndex = hourly.time.indexOfFirst { timeStr ->
            try {
                val t = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                t.hour == now.hour && t.toLocalDate() == now.toLocalDate()
            } catch (_: Exception) { false }
        }
        if (currentIndex < 0) return PollenData()
        return PollenData(
            alder = PollenReading.fromConcentration(hourly.alderPollen?.getOrNull(currentIndex), "Alder", PollenThresholdsDb.ALDER),
            birch = PollenReading.fromConcentration(hourly.birchPollen?.getOrNull(currentIndex), "Birch", PollenThresholdsDb.BIRCH),
            grass = PollenReading.fromConcentration(hourly.grassPollen?.getOrNull(currentIndex), "Grass", PollenThresholdsDb.GRASS),
            mugwort = PollenReading.fromConcentration(hourly.mugwortPollen?.getOrNull(currentIndex), "Mugwort", PollenThresholdsDb.MUGWORT),
            olive = PollenReading.fromConcentration(hourly.olivePollen?.getOrNull(currentIndex), "Olive", PollenThresholdsDb.OLIVE),
            ragweed = PollenReading.fromConcentration(hourly.ragweedPollen?.getOrNull(currentIndex), "Ragweed", PollenThresholdsDb.RAGWEED),
            moldSpores = PollenReading.fromConcentration(hourly.mouldSpores?.getOrNull(currentIndex), "Mold", PollenThresholdsDb.MOLD_SPORES),
        )
    }
}
