package com.sysadmindoc.nimbus.wear.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "WeatherDataListener"
private const val PATH_WEATHER = "/weather/current"

/**
 * Receives weather data pushed from the paired phone via the DataLayer API.
 * Parses the DataMap and persists it in [SyncedWeatherStore] so the watch
 * can display phone-sourced weather without its own network calls.
 */
@AndroidEntryPoint
class WeatherDataListenerService : WearableListenerService() {

    @Inject lateinit var store: SyncedWeatherStore

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH_WEATHER) continue

            try {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap

                // Hourly entries
                val hourlyMaps = map.getDataMapArrayList("hourly") ?: emptyList()
                val hourly = hourlyMaps.map { h ->
                    HourlyEntry(
                        time = h.getString("time", ""),
                        temperature = h.getInt("temperature", 0),
                        weatherCode = h.getInt("weatherCode", 0),
                        precipChance = h.getInt("precipChance", 0),
                        windSpeed = h.getInt("windSpeed", 0),
                    )
                }

                // Daily entries
                val dailyMaps = map.getDataMapArrayList("daily") ?: emptyList()
                val daily = dailyMaps.map { d ->
                    WearDailyEntry(
                        date = d.getString("date", ""),
                        weatherCode = d.getInt("weatherCode", 0),
                        high = d.getInt("high", 0),
                        low = d.getInt("low", 0),
                        precipChance = d.getInt("precipChance", 0),
                    )
                }

                // Alerts
                val alertMaps = map.getDataMapArrayList("alerts") ?: emptyList()
                val alerts = alertMaps.map { a ->
                    WearAlertEntry(
                        event = a.getString("event", ""),
                        severity = a.getString("severity", "Unknown"),
                        headline = a.getString("headline", ""),
                        expires = a.getString("expires", ""),
                    )
                }

                store.save(
                    temperature = map.getInt("temperature", 0),
                    condition = map.getString("condition", "Unknown"),
                    high = map.getInt("high", 0),
                    low = map.getInt("low", 0),
                    locationName = map.getString("locationName", "Unknown"),
                    humidity = map.getInt("humidity", 0),
                    windSpeed = map.getInt("windSpeed", 0),
                    uvIndex = map.getInt("uvIndex", 0),
                    precipChance = map.getInt("precipChance", 0),
                    isDay = map.getBoolean("isDay", true),
                    weatherCode = map.getInt("weatherCode", 0),
                    timestampMs = map.getLong("syncTimestampMs", System.currentTimeMillis()),
                    hourly = hourly,
                    daily = daily,
                    alerts = alerts,
                    aqi = map.getInt("aqi", -1),
                    aqiLabel = map.getString("aqiLabel", ""),
                )

                Log.d(TAG, "Received weather sync: ${map.getString("locationName")} ${map.getInt("temperature")}° (${alerts.size} alerts, ${daily.size} daily)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process synced weather data", e)
            }
        }
    }
}
