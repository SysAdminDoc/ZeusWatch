package com.sysadmindoc.nimbus.wear.sync

import android.content.Context
import android.content.SharedPreferences
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
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
private const val KEY_DAILY_COUNT = "dailyCount"
private const val KEY_ALERT_COUNT = "alertCount"
private const val KEY_AQI = "aqi"
private const val KEY_AQI_LABEL = "aqiLabel"
private const val KEY_TEMP_UNIT = "tempUnit"
private const val KEY_WIND_UNIT = "windUnit"
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

    fun save(payload: SyncedWeatherPayload) {
        // commit() (not apply()) so a sync that arrives moments before the
        // WearableListenerService process is killed actually lands on disk —
        // the Wear OS system kills these services aggressively after they
        // return from onDataChanged, and apply()'s async write is allowed
        // to be dropped if the process exits first.
        val hourly = payload.hourly
        val daily = payload.daily
        val alerts = payload.alerts
        val previousCounts = Triple(
            prefs.getInt(KEY_HOURLY_COUNT, 0),
            prefs.getInt(KEY_DAILY_COUNT, 0),
            prefs.getInt(KEY_ALERT_COUNT, 0),
        )
        val editor = prefs.edit()
        editor.putInt(KEY_TEMPERATURE, payload.temperature)
        editor.putString(KEY_CONDITION, payload.condition)
        editor.putInt(KEY_HIGH, payload.high)
        editor.putInt(KEY_LOW, payload.low)
        editor.putString(KEY_LOCATION_NAME, payload.locationName)
        editor.putInt(KEY_HUMIDITY, payload.humidity)
        editor.putInt(KEY_WIND_SPEED, payload.windSpeed)
        editor.putInt(KEY_UV_INDEX, payload.uvIndex)
        editor.putInt(KEY_PRECIP_CHANCE, payload.precipChance)
        editor.putBoolean(KEY_IS_DAY, payload.isDay)
        editor.putInt(KEY_WEATHER_CODE, payload.weatherCode)
        editor.putLong(KEY_SYNC_TIMESTAMP, payload.timestampMs)
        editor.putString(KEY_TEMP_UNIT, payload.tempUnit)
        editor.putString(KEY_WIND_UNIT, payload.windUnit)
        // null AQI means "not fetched in this sync" — keep the stored value.
        if (payload.aqi != null) editor.putInt(KEY_AQI, payload.aqi)
        if (payload.aqiLabel != null) editor.putString(KEY_AQI_LABEL, payload.aqiLabel)

        editor.putInt(KEY_HOURLY_COUNT, hourly.size)
        hourly.forEachIndexed { i, entry ->
            editor.putString("hourly_${i}_time", entry.time)
            editor.putInt("hourly_${i}_temp", entry.temperature)
            editor.putInt("hourly_${i}_code", entry.weatherCode)
            editor.putInt("hourly_${i}_precip", entry.precipChance)
            editor.putInt("hourly_${i}_wind", entry.windSpeed)
            editor.putBoolean("hourly_${i}_isday", entry.isDay)
        }
        // Remove any stale indexed keys from a previous (larger) save so
        // the prefs file doesn't grow without bound and a future reader
        // can't accidentally pick up data from an old, larger payload.
        for (i in hourly.size until previousCounts.first) {
            editor.remove("hourly_${i}_time")
            editor.remove("hourly_${i}_temp")
            editor.remove("hourly_${i}_code")
            editor.remove("hourly_${i}_precip")
            editor.remove("hourly_${i}_wind")
            editor.remove("hourly_${i}_isday")
        }

        editor.putInt(KEY_DAILY_COUNT, daily.size)
        daily.forEachIndexed { i, entry ->
            editor.putString("daily_${i}_date", entry.date)
            editor.putInt("daily_${i}_code", entry.weatherCode)
            editor.putInt("daily_${i}_high", entry.high)
            editor.putInt("daily_${i}_low", entry.low)
            editor.putInt("daily_${i}_precip", entry.precipChance)
        }
        for (i in daily.size until previousCounts.second) {
            editor.remove("daily_${i}_date")
            editor.remove("daily_${i}_code")
            editor.remove("daily_${i}_high")
            editor.remove("daily_${i}_low")
            editor.remove("daily_${i}_precip")
        }

        // null alerts means "not fetched in this sync" — preserve the stored
        // list. An empty (non-null) list still clears it.
        if (alerts != null) {
            editor.putInt(KEY_ALERT_COUNT, alerts.size)
            alerts.forEachIndexed { i, alert ->
                editor.putString("alert_${i}_event", alert.event)
                editor.putString("alert_${i}_severity", alert.severity)
                editor.putString("alert_${i}_headline", alert.headline)
                editor.putString("alert_${i}_expires", alert.expires)
            }
            for (i in alerts.size until previousCounts.third) {
                editor.remove("alert_${i}_event")
                editor.remove("alert_${i}_severity")
                editor.remove("alert_${i}_headline")
                editor.remove("alert_${i}_expires")
            }
        }

        editor.commit()
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
                isDay = prefs.getBoolean("hourly_${i}_isday", true),
            )
        }

        val dailyCount = prefs.getInt(KEY_DAILY_COUNT, 0)
        val daily = (0 until dailyCount).mapNotNull { i ->
            val date = prefs.getString("daily_${i}_date", null) ?: return@mapNotNull null
            WearDailyEntry(
                date = date,
                weatherCode = prefs.getInt("daily_${i}_code", 0),
                high = prefs.getInt("daily_${i}_high", 0),
                low = prefs.getInt("daily_${i}_low", 0),
                precipChance = prefs.getInt("daily_${i}_precip", 0),
            )
        }

        val alertCount = prefs.getInt(KEY_ALERT_COUNT, 0)
        val alerts = (0 until alertCount).mapNotNull { i ->
            val event = prefs.getString("alert_${i}_event", null) ?: return@mapNotNull null
            WearAlertEntry(
                event = event,
                severity = prefs.getString("alert_${i}_severity", "Unknown") ?: "Unknown",
                headline = prefs.getString("alert_${i}_headline", "") ?: "",
                expires = prefs.getString("alert_${i}_expires", "") ?: "",
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
            daily = daily,
            alerts = alerts,
            aqi = prefs.getInt(KEY_AQI, -1),
            aqiLabel = prefs.getString(KEY_AQI_LABEL, "") ?: "",
            tempUnit = lastTempUnit(),
            windUnit = lastWindUnit(),
        )
    }

    /** Timestamp of the last successful sync, or 0 if never synced. */
    fun lastSyncTimestamp(): Long = prefs.getLong(KEY_SYNC_TIMESTAMP, 0L)

    /**
     * Last display units synced from the phone (metric defaults when never
     * synced). Kept readable even when the weather payload itself has gone
     * stale so the watch's direct-API fallback renders in the user's units.
     */
    fun lastTempUnit(): String =
        prefs.getString(KEY_TEMP_UNIT, null) ?: WearUnitFormatter.TEMP_CELSIUS

    fun lastWindUnit(): String =
        prefs.getString(KEY_WIND_UNIT, null) ?: WearUnitFormatter.WIND_KMH
}

data class SyncedWeatherPayload(
    val temperature: Int,
    val condition: String,
    val high: Int,
    val low: Int,
    val locationName: String,
    val humidity: Int,
    val windSpeed: Int,
    val uvIndex: Int,
    val precipChance: Int,
    val isDay: Boolean,
    val weatherCode: Int,
    val timestampMs: Long,
    val hourly: List<HourlyEntry>,
    val daily: List<WearDailyEntry> = emptyList(),
    /** null = alerts not fetched in this sync (preserve stored); empty = none active (clear). */
    val alerts: List<WearAlertEntry>? = null,
    /** null = AQI not fetched in this sync (preserve stored); -1 = explicitly no data. */
    val aqi: Int? = null,
    val aqiLabel: String? = null,
    /** Display units from the phone's settings ("CELSIUS"/"FAHRENHEIT", "KMH"/"MPH"/"MS"/"KNOTS"). */
    val tempUnit: String = WearUnitFormatter.TEMP_CELSIUS,
    val windUnit: String = WearUnitFormatter.WIND_KMH,
)
