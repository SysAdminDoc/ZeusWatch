package com.sysadmindoc.nimbus.wear.sync

import android.content.ComponentName
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.sysadmindoc.nimbus.wear.complication.WeatherComplicationService
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.data.WearUnitFormatter
import com.sysadmindoc.nimbus.wear.tile.WeatherTileService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "WeatherDataListener"
private const val PATH_WEATHER = "/weather/current"
private const val MAX_HOURLY_ENTRIES = 12
private const val MAX_DAILY_ENTRIES = 7
internal const val MAX_ALERT_ENTRIES = 8
private const val MAX_SHORT_TEXT_CHARS = 80
private const val MAX_ALERT_EVENT_CHARS = 120
private const val MAX_ALERT_HEADLINE_CHARS = 240
private const val MAX_FUTURE_CLOCK_SKEW_MS = 5 * 60 * 1000L
private const val MAX_PAST_CLOCK_SKEW_MS = 5 * 60 * 1000L

/**
 * Receives weather data pushed from the paired phone via the DataLayer API.
 * Parses the DataMap and persists it in [SyncedWeatherStore] so the watch
 * can display phone-sourced weather without its own network calls.
 *
 * All numeric values arrive in canonical metric (°C / km/h); the payload's
 * sanity clamps therefore stay in metric space. Display conversion happens
 * at render time via [WearUnitFormatter] using the synced unit strings.
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
                val payload = map.toSyncedWeatherPayload()
                store.save(payload)

                Log.d(TAG, "Received weather sync: ${payload.locationName} ${payload.temperature}° (${payload.alerts?.size ?: "unchanged"} alerts, ${payload.daily.size} daily)")

                try {
                    ComplicationDataSourceUpdateRequester.create(
                        applicationContext,
                        ComponentName(applicationContext, WeatherComplicationService::class.java),
                    ).requestUpdateAll()
                } catch (_: Exception) { /* Complication may not be active */ }

                try {
                    androidx.wear.tiles.TileService.getUpdater(applicationContext)
                        .requestUpdate(WeatherTileService::class.java)
                } catch (_: Exception) { /* Tile may not be active */ }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process synced weather data", e)
            }
        }
    }
}

internal fun DataMap.toSyncedWeatherPayload(
    receivedAtMs: Long = System.currentTimeMillis(),
): SyncedWeatherPayload {
    val hourly = (getDataMapArrayList("hourly") ?: arrayListOf())
        .take(MAX_HOURLY_ENTRIES)
        .map { h ->
            HourlyEntry(
                time = h.cleanString("time", "", MAX_SHORT_TEXT_CHARS),
                temperature = h.boundedInt("temperature", 0, -100, 100),
                weatherCode = h.boundedInt("weatherCode", 0, 0, 99),
                precipChance = h.boundedInt("precipChance", 0, 0, 100),
                windSpeed = h.boundedInt("windSpeed", 0, 0, 500),
                isDay = h.getBoolean("isDay", true),
            )
        }

    val daily = (getDataMapArrayList("daily") ?: arrayListOf())
        .take(MAX_DAILY_ENTRIES)
        .map { d ->
            WearDailyEntry(
                date = d.cleanString("date", "", MAX_SHORT_TEXT_CHARS),
                weatherCode = d.boundedInt("weatherCode", 0, 0, 99),
                high = d.boundedInt("high", 0, -100, 100),
                low = d.boundedInt("low", 0, -100, 100),
                precipChance = d.boundedInt("precipChance", 0, 0, 100),
            )
        }

    // Absent key = "alerts not fetched in this sync" — keep the previously
    // stored alerts. An empty list still means "fetched, none active".
    val alerts = if (containsKey("alerts")) {
        (getDataMapArrayList("alerts") ?: arrayListOf())
            .take(MAX_ALERT_ENTRIES)
            .map { a ->
                WearAlertEntry(
                    event = a.cleanString("event", "", MAX_ALERT_EVENT_CHARS),
                    severity = a.cleanString("severity", "Unknown", MAX_SHORT_TEXT_CHARS),
                    headline = a.cleanString("headline", "", MAX_ALERT_HEADLINE_CHARS),
                    expires = a.cleanString("expires", "", MAX_SHORT_TEXT_CHARS),
                )
            }
    } else {
        null
    }

    return SyncedWeatherPayload(
        temperature = boundedInt("temperature", 0, -100, 100),
        condition = cleanString("condition", "Unknown", MAX_SHORT_TEXT_CHARS),
        high = boundedInt("high", 0, -100, 100),
        low = boundedInt("low", 0, -100, 100),
        locationName = cleanString("locationName", "Unknown", MAX_SHORT_TEXT_CHARS),
        humidity = boundedInt("humidity", 0, 0, 100),
        windSpeed = boundedInt("windSpeed", 0, 0, 500),
        uvIndex = boundedInt("uvIndex", 0, 0, 25),
        precipChance = boundedInt("precipChance", 0, 0, 100),
        isDay = getBoolean("isDay", true),
        weatherCode = boundedInt("weatherCode", 0, 0, 99),
        timestampMs = sanitizedTimestamp(receivedAtMs),
        hourly = hourly,
        daily = daily,
        alerts = alerts,
        // Same omit-when-unknown convention for AQI.
        aqi = if (containsKey("aqi")) boundedInt("aqi", -1, -1, 500) else null,
        aqiLabel = if (containsKey("aqiLabel")) cleanString("aqiLabel", "", MAX_SHORT_TEXT_CHARS) else null,
        tempUnit = cleanString("tempUnit", WearUnitFormatter.TEMP_CELSIUS, MAX_SHORT_TEXT_CHARS),
        windUnit = cleanString("windUnit", WearUnitFormatter.WIND_KMH, MAX_SHORT_TEXT_CHARS),
    )
}

private fun DataMap.cleanString(key: String, defaultValue: String, maxChars: Int): String {
    val value = getString(key, defaultValue)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: defaultValue
    return value.take(maxChars)
}

private fun DataMap.boundedInt(key: String, defaultValue: Int, min: Int, max: Int): Int =
    getInt(key, defaultValue).coerceIn(min, max)

private fun DataMap.sanitizedTimestamp(receivedAtMs: Long): Long {
    val syncedAt = getLong("syncTimestampMs", receivedAtMs)
    return when {
        syncedAt <= 0L -> receivedAtMs
        syncedAt > receivedAtMs + MAX_FUTURE_CLOCK_SKEW_MS -> receivedAtMs
        // A phone clock running behind would stamp every payload "old" and
        // make fresh syncs look instantly stale — clamp up to receive time.
        syncedAt < receivedAtMs - MAX_PAST_CLOCK_SKEW_MS -> receivedAtMs
        else -> syncedAt
    }
}
