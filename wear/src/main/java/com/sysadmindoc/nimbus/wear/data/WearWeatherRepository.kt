package com.sysadmindoc.nimbus.wear.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight weather repository for Wear OS.
 * Uses OkHttp directly (no Retrofit) for minimal APK size.
 * Fetches only the data needed for watch face: current temp, condition, high/low.
 */
@Singleton
class WearWeatherRepository @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun getCurrentWeather(
        lat: Double = 39.8, // Default: US center (will be replaced by phone sync)
        lon: Double = -98.5,
    ): Result<WearWeatherData> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m,uv_index,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ZeusWatch-Wear/1.3.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val data = json.decodeFromString(WearApiResponse.serializer(), body)

            val current = data.current ?: return@withContext Result.failure(Exception("No current data"))
            val daily = data.daily

            Result.success(WearWeatherData(
                temperature = current.temperature?.toInt() ?: 0,
                condition = wmoDescription(current.weatherCode ?: 0),
                high = daily?.temperatureMax?.firstOrNull()?.toInt() ?: current.temperature?.toInt() ?: 0,
                low = daily?.temperatureMin?.firstOrNull()?.toInt() ?: current.temperature?.toInt() ?: 0,
                locationName = "Current Location",
                humidity = current.humidity ?: 0,
                windSpeed = current.windSpeed?.toInt() ?: 0,
                uvIndex = current.uvIndex?.toInt() ?: 0,
                precipChance = daily?.precipProbMax?.firstOrNull() ?: 0,
                isDay = (current.isDay ?: 1) == 1,
                weatherCode = current.weatherCode ?: 0,
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun wmoDescription(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        in 45..48 -> "Fog"
        in 51..55 -> "Drizzle"
        in 56..57 -> "Freezing Drizzle"
        in 61..65 -> "Rain"
        in 66..67 -> "Freezing Rain"
        in 71..75 -> "Snow"
        77 -> "Snow Grains"
        in 80..82 -> "Showers"
        in 85..86 -> "Snow Showers"
        95 -> "Thunderstorm"
        in 96..99 -> "Thunderstorm + Hail"
        else -> "Unknown"
    }
}

data class WearWeatherData(
    val temperature: Int,
    val condition: String,
    val high: Int,
    val low: Int,
    val locationName: String,
    val humidity: Int = 0,
    val windSpeed: Int = 0,
    val uvIndex: Int = 0,
    val precipChance: Int = 0,
    val isDay: Boolean = true,
    val weatherCode: Int = 0,
)

@Serializable
private data class WearApiResponse(
    val current: WearApiCurrent? = null,
    val daily: WearApiDaily? = null,
)

@Serializable
private data class WearApiCurrent(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

@Serializable
private data class WearApiDaily(
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @SerialName("precipitation_probability_max") val precipProbMax: List<Int?>? = null,
)
