package com.sysadmindoc.nimbus.data.api

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.PwAlert
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] for Pirate Weather v2.8+ WMO-sourced alerts.
 * Requires a user-configured API key. Returns empty when no key is set.
 * Global coverage via WMO data since v2.8 (Nov 2025).
 */
@Singleton
class PirateWeatherAlertAdapter @Inject constructor(
    private val api: PirateWeatherApi,
    private val prefs: UserPreferences,
) : AlertSourceAdapter {

    override val sourceId = "pirate_weather"
    override val displayName = "Pirate Weather"
    override val supportedRegions = setOf("GLOBAL")
    override val isMetered = true

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        return try {
            val apiKey = prefs.settings.first().pirateWeatherApiKey
            if (apiKey.isBlank()) return Result.success(emptyList())

            val response = api.getForecast(apiKey, lat, lon, exclude = "minutely,hourly,daily")
            val alerts = response.alerts.mapNotNull { mapToAlert(it) }
            Result.success(alerts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // 404/400 means "no alerts for this point" — same contract as
            // NwsAlertAdapter. Use the HTTP code, not the message string.
            if (e.code() == 404 || e.code() == 400) {
                Result.success(emptyList())
            } else {
                Log.w(TAG, "Pirate Weather alert fetch failed: HTTP ${e.code()}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pirate Weather alert fetch failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun mapToAlert(pw: PwAlert): WeatherAlert? {
        if (pw.title.isBlank()) return null
        return WeatherAlert(
            id = "pw_${pw.time}_${pw.title.hashCode()}",
            event = pw.title,
            headline = pw.title,
            description = pw.description,
            instruction = null,
            // Pirate Weather severity is a CAP string ("Extreme" / "Severe" /
            // "Moderate" / "Minor" / "Unknown"), not "warning"/"watch"/"advisory".
            severity = AlertSeverity.from(pw.severity),
            urgency = AlertUrgency.UNKNOWN,
            certainty = "Unknown",
            senderName = "Pirate Weather",
            areaDescription = pw.regions.joinToString(", "),
            effective = epochToIso(pw.time),
            expires = epochToIso(pw.expires),
            response = null,
        )
    }

    private fun epochToIso(epoch: Long): String? {
        if (epoch <= 0) return null
        return Instant.ofEpochSecond(epoch)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    companion object {
        private const val TAG = "PwAlertAdapter"
    }
}
