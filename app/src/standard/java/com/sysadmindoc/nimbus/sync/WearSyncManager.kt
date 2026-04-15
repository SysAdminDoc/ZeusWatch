package com.sysadmindoc.nimbus.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearSyncManager"
private const val PATH_WEATHER = "/weather/current"

/**
 * Pushes weather data from the phone to a paired Wear OS device via the
 * DataLayer API. Called after every successful weather fetch so the watch
 * can display phone-sourced data without making its own network calls.
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun syncWeather(
        data: WeatherData,
        alerts: List<WeatherAlert> = emptyList(),
        airQuality: AirQualityData? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val request = PutDataMapRequest.create(PATH_WEATHER).apply {
                dataMap.apply {
                    putInt("temperature", data.current.temperature.toInt())
                    putString("condition", data.current.weatherCode.description)
                    putInt("high", data.current.dailyHigh.toInt())
                    putInt("low", data.current.dailyLow.toInt())
                    putString("locationName", data.location.name)
                    putInt("humidity", data.current.humidity)
                    putInt("windSpeed", data.current.windSpeed.toInt())
                    putInt("uvIndex", data.current.uvIndex.toInt())
                    putInt("precipChance", data.daily.firstOrNull()?.precipitationProbability ?: 0)
                    putBoolean("isDay", data.current.isDay)
                    putInt("weatherCode", data.current.weatherCode.code)
                    putLong("syncTimestampMs", System.currentTimeMillis())

                    // Hourly entries (next 12 hours for watch display)
                    val hourlyMaps = ArrayList<DataMap>()
                    data.hourly.take(12).forEach { h ->
                        hourlyMaps.add(DataMap().apply {
                            putString("time", h.time.toString())
                            putInt("temperature", h.temperature.toInt())
                            putInt("weatherCode", h.weatherCode.code)
                            putInt("precipChance", h.precipitationProbability)
                            putInt("windSpeed", h.windSpeed?.toInt() ?: 0)
                        })
                    }
                    putDataMapArrayList("hourly", hourlyMaps)

                    // Daily forecast (next 7 days)
                    val dailyMaps = ArrayList<DataMap>()
                    data.daily.take(7).forEach { d ->
                        dailyMaps.add(DataMap().apply {
                            putString("date", d.date.toString())
                            putInt("weatherCode", d.weatherCode.code)
                            putInt("high", d.temperatureHigh.toInt())
                            putInt("low", d.temperatureLow.toInt())
                            putInt("precipChance", d.precipitationProbability)
                        })
                    }
                    putDataMapArrayList("daily", dailyMaps)

                    // Weather alerts
                    val alertMaps = ArrayList<DataMap>()
                    alerts.forEach { a ->
                        alertMaps.add(DataMap().apply {
                            putString("event", a.event)
                            putString("severity", a.severity.label)
                            putString("headline", a.headline)
                            putString("expires", a.expires ?: "")
                        })
                    }
                    putDataMapArrayList("alerts", alertMaps)

                    // Air quality
                    if (airQuality != null) {
                        putInt("aqi", airQuality.usAqi)
                        putString("aqiLabel", airQuality.aqiLevel.label)
                    } else {
                        putInt("aqi", -1)
                        putString("aqiLabel", "")
                    }
                }
                setUrgent()
            }

            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest())
                .await()

            Log.d(TAG, "Synced weather to watch: ${data.location.name} ${data.current.temperature}° (${alerts.size} alerts, AQI=${airQuality?.usAqi ?: -1})")
        } catch (e: Exception) {
            // Non-fatal — watch falls back to its own API calls
            Log.w(TAG, "Failed to sync weather to watch", e)
        }
    }
}
