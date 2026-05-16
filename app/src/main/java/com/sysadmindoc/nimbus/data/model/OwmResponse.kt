package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenWeatherMap One Call 3.0 response models.
 * Units: metric (Celsius, m/s, mm, hPa).
 */

@Serializable
data class OwmOneCallResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String = "",
    @SerialName("timezone_offset") val timezoneOffset: Int = 0,
    val current: OwmCurrent? = null,
    val hourly: List<OwmHourly> = emptyList(),
    val daily: List<OwmDaily> = emptyList(),
    val alerts: List<OwmAlert> = emptyList(),
)

@Serializable
data class OwmCurrent(
    val dt: Long,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val pressure: Int = 0,
    val humidity: Int = 0,
    @SerialName("dew_point") val dewPoint: Double? = null,
    val uvi: Double = 0.0,
    val clouds: Int = 0,
    val visibility: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double = 0.0,
    @SerialName("wind_deg") val windDeg: Int = 0,
    @SerialName("wind_gust") val windGust: Double? = null,
    val weather: List<OwmWeatherDesc> = emptyList(),
    val rain: OwmPrecipVolume? = null,
    val snow: OwmPrecipVolume? = null,
)

@Serializable
data class OwmHourly(
    val dt: Long,
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double? = null,
    val pressure: Int? = null,
    val humidity: Int? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    val uvi: Double? = null,
    val clouds: Int? = null,
    val visibility: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_deg") val windDeg: Int? = null,
    @SerialName("wind_gust") val windGust: Double? = null,
    val weather: List<OwmWeatherDesc> = emptyList(),
    val pop: Double? = null, // Probability of precipitation 0-1
    val rain: OwmPrecipVolume? = null,
    val snow: OwmPrecipVolume? = null,
)

@Serializable
data class OwmDaily(
    val dt: Long,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val summary: String? = null,
    val temp: OwmDailyTemp,
    @SerialName("feels_like") val feelsLike: OwmDailyFeelsLike? = null,
    val pressure: Int? = null,
    val humidity: Int? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_deg") val windDeg: Int? = null,
    @SerialName("wind_gust") val windGust: Double? = null,
    val weather: List<OwmWeatherDesc> = emptyList(),
    val clouds: Int? = null,
    val pop: Double? = null,
    val rain: Double? = null,
    val snow: Double? = null,
    val uvi: Double? = null,
)

@Serializable
data class OwmDailyTemp(
    val day: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val night: Double = 0.0,
    val eve: Double = 0.0,
    val morn: Double = 0.0,
)

@Serializable
data class OwmDailyFeelsLike(
    val day: Double = 0.0,
    val night: Double = 0.0,
    val eve: Double = 0.0,
    val morn: Double = 0.0,
)

@Serializable
data class OwmWeatherDesc(
    val id: Int,
    val main: String = "",
    val description: String = "",
    val icon: String = "",
)

@Serializable
data class OwmPrecipVolume(
    @SerialName("1h") val oneHour: Double? = null,
)

@Serializable
data class OwmAlert(
    @SerialName("sender_name") val senderName: String = "",
    val event: String = "",
    val start: Long = 0,
    val end: Long = 0,
    val description: String = "",
    val tags: List<String> = emptyList(),
)

// ── Air Pollution ───────────────────────────────────────────────────────

@Serializable
data class OwmAirPollutionResponse(
    val list: List<OwmAirPollutionEntry> = emptyList(),
)

@Serializable
data class OwmAirPollutionEntry(
    val dt: Long = 0,
    val main: OwmAirPollutionMain,
    val components: OwmAirComponents,
)

@Serializable
data class OwmAirPollutionMain(
    val aqi: Int = 0, // 1-5 scale
)

@Serializable
data class OwmAirComponents(
    val co: Double = 0.0,    // μg/m3
    val no: Double = 0.0,
    val no2: Double = 0.0,
    val o3: Double = 0.0,
    val so2: Double = 0.0,
    val pm2_5: Double = 0.0,
    val pm10: Double = 0.0,
    val nh3: Double = 0.0,
)

/**
 * Maps OWM condition ID to WMO weather code.
 * OWM codes: https://openweathermap.org/weather-conditions
 */
object OwmConditionMapper {
    /**
     * Static lookup table from OpenWeatherMap condition ID to WMO weather code.
     *
     * Table-driven instead of a flat `when` so the cyclomatic complexity
     * stays low and the OWM → WMO contract is one obvious place to audit
     * when OpenWeatherMap revises their condition codes.
     *
     * Built once on first access from a list of (range, wmoCode, comment) triples
     * via the [owmGroup] helper, which expands each [IntRange] into individual
     * keys for O(1) lookup at runtime.
     */
    private val OWM_TO_WMO: Map<Int, Int> = buildMap {
        // Thunderstorm group (2xx) → WMO 95
        owmGroup(200..202, 95)        // Thunderstorm with rain
        owmGroup(210..212, 95)        // Thunderstorm
        put(221, 95)                  // Ragged thunderstorm
        owmGroup(230..232, 95)        // Thunderstorm with drizzle

        // Drizzle group (3xx)
        putAll(listOf(300, 310), 51)  // Light drizzle
        putAll(listOf(301, 311, 313, 321), 53) // Drizzle
        putAll(listOf(302, 312, 314), 55) // Heavy drizzle

        // Rain group (5xx)
        put(500, 61)                  // Light rain
        put(501, 63)                  // Moderate rain
        putAll(listOf(502, 503, 504), 65) // Heavy rain
        put(511, 66)                  // Freezing rain
        put(520, 80)                  // Light showers
        put(521, 81)                  // Showers
        putAll(listOf(522, 531), 82)  // Heavy showers

        // Snow group (6xx)
        put(600, 71)                  // Light snow
        put(601, 73)                  // Snow
        put(602, 75)                  // Heavy snow
        putAll(listOf(611, 612, 613), 77) // Sleet/snow grains
        putAll(listOf(615, 616), 71)  // Rain and snow (light)
        put(620, 85)                  // Light snow showers
        putAll(listOf(621, 622), 86)  // Snow showers

        // Atmosphere group (7xx)
        putAll(listOf(701, 711, 721, 741), 45) // Fog/mist/haze/smoke
        putAll(listOf(731, 751, 761, 762), 45) // Dust/sand/ash
        put(771, 3)                   // Squall → overcast
        put(781, 99)                  // Tornado → severe thunderstorm

        // Clear / Clouds (8xx)
        put(800, 0)                   // Clear sky
        put(801, 1)                   // Few clouds
        put(802, 2)                   // Scattered clouds
        putAll(listOf(803, 804), 3)   // Overcast
    }

    fun toWmoCode(owmId: Int): Int = OWM_TO_WMO[owmId] ?: -1

    fun isDayFromIcon(icon: String): Boolean = icon.endsWith("d")

    private fun MutableMap<Int, Int>.owmGroup(range: IntRange, wmoCode: Int) {
        for (id in range) put(id, wmoCode)
    }

    private fun MutableMap<Int, Int>.putAll(ids: List<Int>, wmoCode: Int) {
        for (id in ids) put(id, wmoCode)
    }
}
