package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.AirQualityApi
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.AstronomyData
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

                val pollen = PollenData(
                    alder = PollenReading.fromConcentration(current.alderPollen, "Alder", PollenThresholdsDb.ALDER),
                    birch = PollenReading.fromConcentration(current.birchPollen, "Birch", PollenThresholdsDb.BIRCH),
                    grass = PollenReading.fromConcentration(current.grassPollen, "Grass", PollenThresholdsDb.GRASS),
                    mugwort = PollenReading.fromConcentration(current.mugwortPollen, "Mugwort", PollenThresholdsDb.MUGWORT),
                    olive = PollenReading.fromConcentration(current.olivePollen, "Olive", PollenThresholdsDb.OLIVE),
                    ragweed = PollenReading.fromConcentration(current.ragweedPollen, "Ragweed", PollenThresholdsDb.RAGWEED),
                )

                // Build hourly AQI (next 24h)
                val now = LocalDateTime.now()
                val hourlyAqi = mutableListOf<HourlyAqi>()
                response.hourly?.let { h ->
                    for (i in h.time.indices) {
                        val t = try {
                            LocalDateTime.parse(h.time[i], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        } catch (_: Exception) { continue }
                        if (t.isBefore(now.minusHours(1))) continue
                        if (hourlyAqi.size >= 24) break
                        val aqi = h.usAqi?.getOrNull(i) ?: continue
                        hourlyAqi.add(HourlyAqi(
                            hour = WeatherFormatter.formatHourLabel(t),
                            aqi = aqi,
                            level = AqiLevel.fromAqi(aqi),
                        ))
                    }
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
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Calculate astronomy data from date.
     * Moon phase uses Conway's algorithm approximation.
     */
    fun getAstronomy(sunrise: String?, sunset: String?): AstronomyData {
        val now = LocalDateTime.now()
        val lunarAge = calculateLunarAge(now.year, now.monthValue, now.dayOfMonth)
        val illumination = calculateIllumination(lunarAge)
        val phase = MoonPhase.fromDayOfCycle(lunarAge)

        // Approximate moonrise/moonset (shifts ~50 min/day after full moon)
        val moonrise = estimateMoonTime(lunarAge, isRise = true)
        val moonset = estimateMoonTime(lunarAge, isRise = false)

        // Day length from sunrise/sunset
        val dayLength = calculateDayLength(sunrise, sunset)

        return AstronomyData(
            moonPhase = phase,
            moonIllumination = illumination,
            moonrise = moonrise,
            moonset = moonset,
            dayLength = dayLength,
        )
    }

    private fun calculateLunarAge(year: Int, month: Int, day: Int): Double {
        // Trig approximation of lunar age
        var y = year.toDouble()
        var m = month.toDouble()
        if (m <= 2) { y -= 1; m += 12 }
        val jd = (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day - 1524.5
        val daysSinceNew = jd - 2451550.1 // Known new moon: Jan 6 2000
        return daysSinceNew % 29.53058867
    }

    private fun calculateIllumination(lunarAge: Double): Double {
        // Approximate illumination from lunar age
        val phase = lunarAge / 29.53058867
        return ((1 - cos(phase * 2 * Math.PI)) / 2 * 100).roundToInt().toDouble()
    }

    private fun estimateMoonTime(lunarAge: Double, isRise: Boolean): String {
        // Very rough approximation
        val baseHour = if (isRise) 18.0 else 6.0
        val shift = (lunarAge / 29.53) * 24.0
        val hour = ((baseHour + shift) % 24).toInt()
        val minute = (((baseHour + shift) % 1) * 60).toInt()
        val amPm = if (hour < 12) "AM" else "PM"
        val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return "%d:%02d %s".format(h12, minute, amPm)
    }

    private fun calculateDayLength(sunrise: String?, sunset: String?): String? {
        if (sunrise == null || sunset == null) return null
        return try {
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val rise = LocalDateTime.parse(sunrise, fmt)
            val set = LocalDateTime.parse(sunset, fmt)
            val minutes = ChronoUnit.MINUTES.between(rise, set)
            "%dh %dm".format(minutes / 60, minutes % 60)
        } catch (_: Exception) { null }
    }
}
