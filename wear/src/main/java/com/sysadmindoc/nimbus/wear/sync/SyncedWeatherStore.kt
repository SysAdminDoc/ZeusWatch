package com.sysadmindoc.nimbus.wear.sync

import android.content.Context
import android.content.SharedPreferences
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "synced_weather"
private const val KEY_TEMPERATURE = "temperature"
private const val KEY_CONDITION = "condition"
private const val KEY_HIGH = "high"
private const val KEY_LOW = "low"
private const val KEY_LOCATION_NAME = "locationName"
private const val KEY_HUMIDITY = "humidity"
private const val KEY_WIND_SPEED = "windSpeed"
private const val KEY_UV_INDEX = "uvIndex"
private const val KEY_PRECIP_CHANCE = "precipChance"
private const val KEY_IS_DAY = "isDay"
private const val KEY_WEATHER_CODE = "weatherCode"
private const val KEY_SYNC_TIMESTAMP = "syncTimestampMs"
private const val KEY_HOURLY_COUNT = "hourlyCount"
private const val MAX_STALENESS_MS = 30 * 60 * 1000L // 30 minutes

/**
 * Persists the most recent phone-synced weather data in SharedPreferences.
 * The [WearWeatherRepository] checks this store before making its own
 * network calls — if the data is fresh, the API call is skipped entirely.
 */
@Singleton
class SyncedWeatherStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(
        temperature: Int,
        condition: String,
        high: Int,
        low: Int,
        locationName: String,
        humidity: Int,
        windSpeed: Int,
        uvIndex: Int,
        precipChance: Int,
        isDay: Boolean,
        weatherCode: Int,
        timestampMs: Long,
        hourly: List<HourlyEntry>,
    ) {
        prefs.edit().apply {
            putInt(KEY_TEMPERATURE, temperature)
            putString(KEY_CONDITION, condition)
            putInt(KEY_HIGH, high)
            putInt(KEY_LOW, low)
            putString(KEY_LOCATION_NAME, locationName)
            putInt(KEY_HUMIDITY, humidity)
            putInt(KEY_WIND_SPEED, windSpeed)
            putInt(KEY_UV_INDEX, uvIndex)
            putInt(KEY_PRECIP_CHANCE, precipChance)
            putBoolean(KEY_IS_DAY, isDay)
            putInt(KEY_WEATHER_CODE, weatherCode)
            putLong(KEY_SYNC_TIMESTAMP, timestampMs)

            // Store hourly entries as indexed keys
            putInt(KEY_HOURLY_COUNT, hourly.size)
            hourly.forEachIndexed { i, entry ->
                putString("hourly_${i}_time", entry.time)
                putInt("hourly_${i}_temp", entry.temperature)
                putInt("hourly_${i}_code", entry.weatherCode)
                putInt("hourly_${i}_precip", entry.precipChance)
                putInt("hourly_${i}_wind", entry.windSpeed)
            }
            apply()
        }
    }

    /**
     * Returns the synced weather data if it exists and is fresh enough,
     * or null if no data has been synced or the data is stale.
     */
    fun getFreshData(): WearWeatherData? {
        val timestamp = prefs.getLong(KEY_SYNC_TIMESTAMP, 0L)
        if (timestamp == 0L) return null
        if (System.currentTimeMillis() - timestamp > MAX_STALENESS_MS) return null

        val locationName = prefs.getString(KEY_LOCATION_NAME, null) ?: return null
        val weatherCode = prefs.getInt(KEY_WEATHER_CODE, -1)
        if (weatherCode == -1) return null

        val hourlyCount = prefs.getInt(KEY_HOURLY_COUNT, 0)
        val hourly = (0 until hourlyCount).mapNotNull { i ->
            val time = prefs.getString("hourly_${i}_time", null) ?: return@mapNotNull null
            HourlyEntry(
                time = time,
                temperature = prefs.getInt("hourly_${i}_temp", 0),
                weatherCode = prefs.getInt("hourly_${i}_code", 0),
                precipChance = prefs.getInt("hourly_${i}_precip", 0),
                windSpeed = prefs.getInt("hourly_${i}_wind", 0),
            )
        }

        return WearWeatherData(
            temperature = prefs.getInt(KEY_TEMPERATURE, 0),
            condition = prefs.getString(KEY_CONDITION, null)
                ?: WearWeatherRepository.wmoDescription(weatherCode),
            high = prefs.getInt(KEY_HIGH, 0),
            low = prefs.getInt(KEY_LOW, 0),
            locationName = locationName,
            humidity = prefs.getInt(KEY_HUMIDITY, 0),
            windSpeed = prefs.getInt(KEY_WIND_SPEED, 0),
            uvIndex = prefs.getInt(KEY_UV_INDEX, 0),
            precipChance = prefs.getInt(KEY_PRECIP_CHANCE, 0),
            isDay = prefs.getBoolean(KEY_IS_DAY, true),
            weatherCode = weatherCode,
            hourly = hourly,
        )
    }

    /** Timestamp of the last successful sync, or 0 if never synced. */
    fun lastSyncTimestamp(): Long = prefs.getLong(KEY_SYNC_TIMESTAMP, 0L)
}
