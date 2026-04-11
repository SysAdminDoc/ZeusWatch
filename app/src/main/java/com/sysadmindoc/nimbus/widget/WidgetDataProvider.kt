package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "nimbus_widget_data")

/**
 * Lightweight widget data cache using DataStore.
 * Widgets read from here; the refresh worker writes to it.
 */
object WidgetDataProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val KEY_LOCATION = stringPreferencesKey("w_location")
    private val KEY_TEMP = doublePreferencesKey("w_temp")
    private val KEY_FEELS_LIKE = doublePreferencesKey("w_feels_like")
    private val KEY_HIGH = doublePreferencesKey("w_high")
    private val KEY_LOW = doublePreferencesKey("w_low")
    private val KEY_WEATHER_CODE = intPreferencesKey("w_code")
    private val KEY_IS_DAY = intPreferencesKey("w_is_day")
    private val KEY_HUMIDITY = intPreferencesKey("w_humidity")
    private val KEY_WIND_SPEED = doublePreferencesKey("w_wind")
    private val KEY_HOURLY_JSON = stringPreferencesKey("w_hourly")
    private val KEY_DAILY_JSON = stringPreferencesKey("w_daily")
    private val KEY_UPDATED_AT = longPreferencesKey("w_updated")

    suspend fun save(context: Context, data: WidgetWeatherData) {
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_LOCATION] = data.locationName
            prefs[KEY_TEMP] = data.temperature
            prefs[KEY_FEELS_LIKE] = data.feelsLike
            prefs[KEY_HIGH] = data.high
            prefs[KEY_LOW] = data.low
            prefs[KEY_WEATHER_CODE] = data.weatherCode
            prefs[KEY_IS_DAY] = if (data.isDay) 1 else 0
            prefs[KEY_HUMIDITY] = data.humidity
            prefs[KEY_WIND_SPEED] = data.windSpeed
            prefs[KEY_HOURLY_JSON] = json.encodeToString(WidgetHourlyList.serializer(), WidgetHourlyList(data.hourly))
            prefs[KEY_DAILY_JSON] = json.encodeToString(WidgetDailyList.serializer(), WidgetDailyList(data.daily))
            prefs[KEY_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun load(context: Context): WidgetWeatherData? {
        val prefs = context.widgetDataStore.data.first()
        val location = prefs[KEY_LOCATION] ?: return null
        val hourlyJson = prefs[KEY_HOURLY_JSON]
        val dailyJson = prefs[KEY_DAILY_JSON]

        return WidgetWeatherData(
            locationName = location,
            temperature = prefs[KEY_TEMP] ?: 0.0,
            feelsLike = prefs[KEY_FEELS_LIKE] ?: 0.0,
            high = prefs[KEY_HIGH] ?: 0.0,
            low = prefs[KEY_LOW] ?: 0.0,
            weatherCode = prefs[KEY_WEATHER_CODE] ?: 0,
            isDay = (prefs[KEY_IS_DAY] ?: 1) == 1,
            humidity = prefs[KEY_HUMIDITY] ?: 0,
            windSpeed = prefs[KEY_WIND_SPEED] ?: 0.0,
            hourly = hourlyJson?.let {
                try { json.decodeFromString(WidgetHourlyList.serializer(), it).items } catch (_: Exception) { emptyList() }
            } ?: emptyList(),
            daily = dailyJson?.let {
                try { json.decodeFromString(WidgetDailyList.serializer(), it).items } catch (_: Exception) { emptyList() }
            } ?: emptyList(),
            updatedAt = prefs[KEY_UPDATED_AT] ?: 0L,
        )
    }

    suspend fun clearDefault(context: Context) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(KEY_LOCATION)
            prefs.remove(KEY_TEMP)
            prefs.remove(KEY_FEELS_LIKE)
            prefs.remove(KEY_HIGH)
            prefs.remove(KEY_LOW)
            prefs.remove(KEY_WEATHER_CODE)
            prefs.remove(KEY_IS_DAY)
            prefs.remove(KEY_HUMIDITY)
            prefs.remove(KEY_WIND_SPEED)
            prefs.remove(KEY_HOURLY_JSON)
            prefs.remove(KEY_DAILY_JSON)
            prefs.remove(KEY_UPDATED_AT)
        }
    }

    // ---- Per-widget keyed variants ----

    private fun wKey(appWidgetId: Int, base: String) = "w_${appWidgetId}_$base"

    /** Save weather data scoped to a specific widget instance. */
    suspend fun save(context: Context, data: WidgetWeatherData, appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs[stringPreferencesKey(wKey(appWidgetId, "location"))] = data.locationName
            prefs[doublePreferencesKey(wKey(appWidgetId, "temp"))] = data.temperature
            prefs[doublePreferencesKey(wKey(appWidgetId, "feels_like"))] = data.feelsLike
            prefs[doublePreferencesKey(wKey(appWidgetId, "high"))] = data.high
            prefs[doublePreferencesKey(wKey(appWidgetId, "low"))] = data.low
            prefs[intPreferencesKey(wKey(appWidgetId, "code"))] = data.weatherCode
            prefs[intPreferencesKey(wKey(appWidgetId, "is_day"))] = if (data.isDay) 1 else 0
            prefs[intPreferencesKey(wKey(appWidgetId, "humidity"))] = data.humidity
            prefs[doublePreferencesKey(wKey(appWidgetId, "wind"))] = data.windSpeed
            prefs[stringPreferencesKey(wKey(appWidgetId, "hourly"))] = json.encodeToString(WidgetHourlyList.serializer(), WidgetHourlyList(data.hourly))
            prefs[stringPreferencesKey(wKey(appWidgetId, "daily"))] = json.encodeToString(WidgetDailyList.serializer(), WidgetDailyList(data.daily))
            prefs[longPreferencesKey(wKey(appWidgetId, "updated"))] = System.currentTimeMillis()
        }
    }

    /** Load weather data for a specific widget, falling back to global data if not found. */
    suspend fun load(context: Context, appWidgetId: Int): WidgetWeatherData? {
        val prefs = context.widgetDataStore.data.first()
        val location = prefs[stringPreferencesKey(wKey(appWidgetId, "location"))]
            ?: return load(context) // fallback to global

        val hourlyJson = prefs[stringPreferencesKey(wKey(appWidgetId, "hourly"))]
        val dailyJson = prefs[stringPreferencesKey(wKey(appWidgetId, "daily"))]

        return WidgetWeatherData(
            locationName = location,
            temperature = prefs[doublePreferencesKey(wKey(appWidgetId, "temp"))] ?: 0.0,
            feelsLike = prefs[doublePreferencesKey(wKey(appWidgetId, "feels_like"))] ?: 0.0,
            high = prefs[doublePreferencesKey(wKey(appWidgetId, "high"))] ?: 0.0,
            low = prefs[doublePreferencesKey(wKey(appWidgetId, "low"))] ?: 0.0,
            weatherCode = prefs[intPreferencesKey(wKey(appWidgetId, "code"))] ?: 0,
            isDay = (prefs[intPreferencesKey(wKey(appWidgetId, "is_day"))] ?: 1) == 1,
            humidity = prefs[intPreferencesKey(wKey(appWidgetId, "humidity"))] ?: 0,
            windSpeed = prefs[doublePreferencesKey(wKey(appWidgetId, "wind"))] ?: 0.0,
            hourly = hourlyJson?.let {
                try { json.decodeFromString(WidgetHourlyList.serializer(), it).items } catch (_: Exception) { emptyList() }
            } ?: emptyList(),
            daily = dailyJson?.let {
                try { json.decodeFromString(WidgetDailyList.serializer(), it).items } catch (_: Exception) { emptyList() }
            } ?: emptyList(),
            updatedAt = prefs[longPreferencesKey(wKey(appWidgetId, "updated"))] ?: 0L,
        )
    }

    suspend fun remove(context: Context, appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(wKey(appWidgetId, "location")))
            prefs.remove(doublePreferencesKey(wKey(appWidgetId, "temp")))
            prefs.remove(doublePreferencesKey(wKey(appWidgetId, "feels_like")))
            prefs.remove(doublePreferencesKey(wKey(appWidgetId, "high")))
            prefs.remove(doublePreferencesKey(wKey(appWidgetId, "low")))
            prefs.remove(intPreferencesKey(wKey(appWidgetId, "code")))
            prefs.remove(intPreferencesKey(wKey(appWidgetId, "is_day")))
            prefs.remove(intPreferencesKey(wKey(appWidgetId, "humidity")))
            prefs.remove(doublePreferencesKey(wKey(appWidgetId, "wind")))
            prefs.remove(stringPreferencesKey(wKey(appWidgetId, "hourly")))
            prefs.remove(stringPreferencesKey(wKey(appWidgetId, "daily")))
            prefs.remove(longPreferencesKey(wKey(appWidgetId, "updated")))
        }
    }
}

data class WidgetWeatherData(
    val locationName: String,
    val temperature: Double,
    val feelsLike: Double,
    val high: Double,
    val low: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windSpeed: Double,
    val hourly: List<WidgetHourly>,
    val daily: List<WidgetDaily>,
    val updatedAt: Long = 0L,
)

@Serializable
data class WidgetHourly(
    val hour: String, // "Now", "3 PM"
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val precipChance: Int,
)

@Serializable
data class WidgetDaily(
    val day: String, // "Today", "Mon", "Tue 3"
    val high: Int,
    val low: Int,
    val code: Int,
    val precipChance: Int,
)

@Serializable
private data class WidgetHourlyList(val items: List<WidgetHourly>)

@Serializable
private data class WidgetDailyList(val items: List<WidgetDaily>)
