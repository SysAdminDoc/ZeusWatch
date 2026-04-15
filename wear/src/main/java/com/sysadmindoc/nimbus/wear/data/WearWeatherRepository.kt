package com.sysadmindoc.nimbus.wear.data

import android.util.Log
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearWeatherRepo"

@Singleton
class WearWeatherRepository @Inject constructor(
    private val client: OkHttpClient,
    private val syncedStore: SyncedWeatherStore,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Returns weather data, preferring phone-synced data when fresh.
     * Falls back to a direct Open-Meteo API call when the phone hasn't
     * synced recently or no paired device is connected.
     */
    suspend fun getCurrentWeather(
        lat: Double,
        lon: Double,
        locationName: String = "Unknown",
    ): Result<WearWeatherData> = withContext(Dispatchers.IO) {
        // Prefer phone-synced data if fresh (< 30 min)
        val synced = syncedStore.getFreshData()
        if (synced != null) {
            Log.d(TAG, "Using phone-synced weather data (age ${(System.currentTimeMillis() - syncedStore.lastSyncTimestamp()) / 1000}s)")
            return@withContext Result.success(synced)
        }
        Log.d(TAG, "No fresh synced data, fetching from API")
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m,uv_index,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m" +
                "&forecast_hours=12" +
                "&timezone=auto&forecast_days=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ZeusWatch-Wear/1.14.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Weather service error (${response.code})"),
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))
                val data = json.decodeFromString<WearApiResponse>(body)
                val current = data.current
                    ?: return@withContext Result.failure(Exception("No current data"))
                val daily = data.daily

                Result.success(
                    WearWeatherData(
                        temperature = current.temperature?.toInt() ?: 0,
                        condition = wmoDescription(current.weatherCode ?: 0),
                        high = daily?.temperatureMax?.firstOrNull()?.toInt()
                            ?: current.temperature?.toInt() ?: 0,
                        low = daily?.temperatureMin?.firstOrNull()?.toInt()
                            ?: current.temperature?.toInt() ?: 0,
                        locationName = locationName,
                        humidity = current.humidity ?: 0,
                        windSpeed = current.windSpeed?.toInt() ?: 0,
                        uvIndex = current.uvIndex?.toInt() ?: 0,
                        precipChance = daily?.precipProbMax?.firstOrNull() ?: 0,
                        isDay = (current.isDay ?: 1) == 1,
                        weatherCode = current.weatherCode ?: 0,
                        hourly = buildHourlyList(data.hourly),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildHourlyList(hourly: WearApiHourly?): List<HourlyEntry> {
        if (hourly == null) return emptyList()
        val times = hourly.time ?: return emptyList()
        return times.mapIndexedNotNull { i, time ->
            if (time == null) return@mapIndexedNotNull null
            HourlyEntry(
                time = time,
                temperature = hourly.temperature?.getOrNull(i)?.toInt() ?: 0,
                weatherCode = hourly.weatherCode?.getOrNull(i) ?: 0,
                precipChance = hourly.precipProb?.getOrNull(i) ?: 0,
                windSpeed = hourly.windSpeed?.getOrNull(i)?.toInt() ?: 0,
            )
        }
    }

    companion object {
        fun wmoDescription(code: Int): String = when (code) {
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

        fun wmoEmoji(code: Int, isDay: Boolean = true): String = when (code) {
            0 -> if (isDay) "\u2600\uFE0F" else "\uD83C\uDF19"
            1 -> if (isDay) "\uD83C\uDF24\uFE0F" else "\uD83C\uDF19"
            2 -> "\u26C5"
            3 -> "\u2601\uFE0F"
            in 45..48 -> "\uD83C\uDF2B\uFE0F"
            in 51..57 -> "\uD83C\uDF27\uFE0F"
            in 61..67 -> "\uD83C\uDF27\uFE0F"
            in 71..77 -> "\uD83C\uDF28\uFE0F"
            in 80..86 -> "\uD83C\uDF27\uFE0F"
            in 95..99 -> "\u26C8\uFE0F"
            else -> "\uD83C\uDF21\uFE0F"
        }
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
    val hourly: List<HourlyEntry> = emptyList(),
    val daily: List<WearDailyEntry> = emptyList(),
    val alerts: List<WearAlertEntry> = emptyList(),
    val aqi: Int = -1,
    val aqiLabel: String = "",
)

data class HourlyEntry(
    val time: String,
    val temperature: Int,
    val weatherCode: Int,
    val precipChance: Int = 0,
    val windSpeed: Int = 0,
)

data class WearDailyEntry(
    val date: String,
    val weatherCode: Int,
    val high: Int,
    val low: Int,
    val precipChance: Int = 0,
)

data class WearAlertEntry(
    val event: String,
    val severity: String,
    val headline: String,
    val expires: String = "",
)

@Serializable
private data class WearApiResponse(
    val current: WearApiCurrent? = null,
    val daily: WearApiDaily? = null,
    val hourly: WearApiHourly? = null,
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

@Serializable
private data class WearApiHourly(
    val time: List<String?>? = null,
    @SerialName("temperature_2m") val temperature: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("precipitation_probability") val precipProb: List<Int?>? = null,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>? = null,
)
