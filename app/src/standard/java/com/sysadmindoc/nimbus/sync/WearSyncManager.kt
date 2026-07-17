package com.sysadmindoc.nimbus.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.util.conditionDescription
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val TAG = "WearSyncManager"
private const val PATH_WEATHER = "/weather/current"

// Mirrors the watch listener's caps (WeatherDataListenerService) so we never
// ship payload bytes the watch is going to truncate anyway.
private const val MAX_ALERT_ENTRIES = 8
private const val MAX_ALERT_EVENT_CHARS = 120
private const val MAX_ALERT_HEADLINE_CHARS = 240

/**
 * Pushes weather data from the phone to a paired Wear OS device via the
 * DataLayer API. Called after every successful weather fetch so the watch
 * can display phone-sourced data without making its own network calls.
 *
 * Values are sent in canonical metric (°C / km/h); the user's display units
 * are sent alongside ("tempUnit"/"windUnit") so the watch converts at render.
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {

    /**
     * @param alerts null means "not fetched in this code path" — the keys are
     *   omitted from the DataMap and the watch keeps its previously stored
     *   alerts. An empty list means "fetched, none active" and clears them.
     * @param airQuality same convention: null omits the AQI keys (watch keeps
     *   its previous value) rather than overwriting with "no data".
     */
    // Block body (not expression) so the return type is Unit, matching the
    // freenet no-op — an expression body would infer Int from the trailing
    // Log.d call and break flavor parity / suspend-Unit mocking.
    suspend fun syncWeather(
        data: WeatherData,
        alerts: List<WeatherAlert>? = null,
        airQuality: AirQualityData? = null,
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val settings = prefs.settings.first()
            val request = PutDataMapRequest.create(PATH_WEATHER).apply {
                dataMap.apply {
                    putInt("temperature", data.current.temperature.roundToInt())
                    // Localized at source (respects the phone locale and any
                    // provider-supplied condition text) so the watch never
                    // renders a raw English enum name.
                    putString("condition", data.current.conditionDescription(context))
                    putInt("high", data.current.dailyHigh.roundToInt())
                    putInt("low", data.current.dailyLow.roundToInt())
                    putString("locationName", data.location.name)
                    putInt("humidity", data.current.humidity)
                    putInt("windSpeed", data.current.windSpeed.roundToInt())
                    putInt("uvIndex", data.current.uvIndex.roundToInt())
                    putInt("precipChance", data.daily.firstOrNull()?.precipitationProbability ?: 0)
                    putBoolean("isDay", data.current.isDay)
                    putInt("weatherCode", data.current.weatherCode.code)
                    putLong("syncTimestampMs", System.currentTimeMillis())
                    // When the weather itself was produced — distinct from
                    // syncTimestampMs so a deliberate cached-data push during
                    // an outage doesn't get stamped "just now" on the watch.
                    // Additive field: old watches simply ignore it.
                    putLong(
                        "dataUpdatedAtMs",
                        data.lastUpdated.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    )

                    // Display units — raw values above stay metric; the watch
                    // converts at render time.
                    putString("tempUnit", settings.tempUnit.name)
                    putString("windUnit", settings.windUnit.name)

                    // Hourly entries (next 12 hours for watch display)
                    val hourlyMaps = ArrayList<DataMap>()
                    data.hourly.take(12).forEach { h ->
                        hourlyMaps.add(DataMap().apply {
                            putString("time", h.time.toString())
                            putInt("temperature", h.temperature.roundToInt())
                            putInt("weatherCode", h.weatherCode.code)
                            putInt("precipChance", h.precipitationProbability)
                            putInt("windSpeed", h.windSpeed?.roundToInt() ?: 0)
                            putBoolean("isDay", h.isDay)
                        })
                    }
                    putDataMapArrayList("hourly", hourlyMaps)

                    // Daily forecast (next 7 days)
                    val dailyMaps = ArrayList<DataMap>()
                    data.daily.take(7).forEach { d ->
                        dailyMaps.add(DataMap().apply {
                            putString("date", d.date.toString())
                            putInt("weatherCode", d.weatherCode.code)
                            putInt("high", d.temperatureHigh.roundToInt())
                            putInt("low", d.temperatureLow.roundToInt())
                            putInt("precipChance", d.precipitationProbability)
                        })
                    }
                    putDataMapArrayList("daily", dailyMaps)

                    // Weather alerts — omit the key entirely when the caller
                    // didn't fetch alerts so the watch preserves its last
                    // known set instead of wiping it every background sync.
                    if (alerts != null) {
                        val alertMaps = ArrayList<DataMap>()
                        alerts.take(MAX_ALERT_ENTRIES).forEach { a ->
                            alertMaps.add(DataMap().apply {
                                putString("event", a.event.take(MAX_ALERT_EVENT_CHARS))
                                putString("severity", a.severity.label)
                                putString("headline", a.headline.take(MAX_ALERT_HEADLINE_CHARS))
                                putString("expires", a.expires ?: "")
                            })
                        }
                        putDataMapArrayList("alerts", alertMaps)
                    }

                    // Air quality — same omit-when-unknown convention.
                    if (airQuality != null) {
                        putInt("aqi", airQuality.usAqi)
                        putString("aqiLabel", airQuality.aqiLevel.label)
                    }
                }
                setUrgent()
            }

            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest())
                .await()

            Log.d(TAG, "Synced weather to watch: ${data.location.name} ${data.current.temperature}° (${alerts?.size ?: "unchanged"} alerts, AQI=${airQuality?.usAqi ?: "unchanged"})")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // Propagate cancellation so the caller's structured concurrency
            // sees the cancel — otherwise a fast `putDataItem().await()`
            // cancellation would be silently masked as a "sync failure"
            // and parent jobs would never unwind.
            throw cancelled
        } catch (e: Exception) {
            // Non-fatal — watch falls back to its own API calls
            if (e is ApiException && e.statusCode == CommonStatusCodes.API_NOT_CONNECTED) {
                Log.d(TAG, "Wear OS sync unavailable on this device")
            } else {
                Log.w(TAG, "Failed to sync weather to watch", e)
            }
        }
    }
}
