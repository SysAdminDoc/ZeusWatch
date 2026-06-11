package com.sysadmindoc.nimbus.data.api

import android.util.Log
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.PwAlert
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import kotlinx.coroutines.flow.first
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

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        return try {
            val apiKey = prefs.settings.first().pirateWeatherApiKey
            if (apiKey.isBlank()) return Result.success(emptyList())

            val response = api.getForecast(apiKey, lat, lon, exclude = "minutely,hourly,daily")
            val alerts = response.alerts.mapNotNull { mapToAlert(it) }
            Result.success(alerts)
        } catch (e: Exception) {
            Log.w(TAG, "Pirate Weather alert fetch failed: ${e.message}")
            Result.success(emptyList())
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
            severity = mapSeverity(pw.severity),
            urgency = AlertUrgency.UNKNOWN,
            certainty = "Unknown",
            senderName = "Pirate Weather",
            areaDescription = pw.regions.joinToString(", "),
            effective = epochToIso(pw.time),
            expires = epochToIso(pw.expires),
            response = null,
        )
    }

    private fun mapSeverity(s: String): AlertSeverity = when (s.lowercase()) {
        "warning" -> AlertSeverity.SEVERE
        "watch" -> AlertSeverity.MODERATE
        "advisory" -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
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
