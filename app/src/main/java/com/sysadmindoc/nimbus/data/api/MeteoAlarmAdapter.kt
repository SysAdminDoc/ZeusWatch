package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] for EUMETNET MeteoAlarm — the European weather warning aggregation
 * service covering 30+ countries.
 *
 * The feed is per-country, so we need the caller to resolve the country code first.
 * We fetch all alerts for the detected country and return them; the repository handles
 * proximity filtering if needed.
 */
@Singleton
class MeteoAlarmAdapter @Inject constructor(
    private val meteoAlarmApi: MeteoAlarmApi,
) : AlertSourceAdapter {

    override val sourceId = "meteoalarm"
    override val displayName = "MeteoAlarm (EUMETNET)"

    override val supportedRegions = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "NO", "PL", "PT", "RO", "RS", "SK", "SI", "ES", "SE", "CH",
        "GB", // UK uses GB in ISO 3166-1
    )

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        // Country detection is handled by AlertRepository before calling us.
        // We receive lat/lon but need the country code — derive it from the repository's
        // country detection and store it in a thread-local or pass via a companion helper.
        // For simplicity, we expose a country-specific overload used by the repository.
        return Result.success(emptyList())
    }

    /**
     * Fetch alerts for a specific country code.
     * Called by [com.sysadmindoc.nimbus.data.repository.AlertRepository] after country detection.
     */
    suspend fun getAlertsForCountry(countryCode: String): Result<List<WeatherAlert>> {
        return try {
            val response = meteoAlarmApi.getWarnings(countryCode.lowercase())
            val alerts = response.warnings.flatMap { warning ->
                warning.info.mapNotNull { info ->
                    val event = info.event ?: return@mapNotNull null
                    val areaDesc = info.area
                        .mapNotNull { it.areaDesc }
                        .joinToString(", ")
                        .ifEmpty { "Unknown area" }
                    WeatherAlert(
                        id = warning.identifier ?: "${sourceId}_${event}_${info.onset}",
                        event = event,
                        headline = info.headline ?: event,
                        description = info.description ?: "",
                        instruction = info.instruction,
                        severity = AlertSeverity.from(info.severity),
                        urgency = AlertUrgency.from(info.urgency),
                        certainty = info.certainty ?: "Unknown",
                        senderName = info.senderName ?: warning.sender ?: displayName,
                        areaDescription = areaDesc,
                        effective = info.onset,
                        expires = info.expires,
                        response = null,
                    )
                }
            }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
