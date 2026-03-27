package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] for the Japan Meteorological Agency (JMA).
 * Covers Japan (JP) only.
 *
 * JMA publishes weather warnings via XML feeds. This adapter consumes a
 * JSON-converted version of the extra warnings feed and maps entries to
 * the common [WeatherAlert] model.
 */
@Singleton
class JmaAlertAdapter @Inject constructor(
    private val jmaApi: JmaAlertApi,
) : AlertSourceAdapter {

    override val sourceId = "jma"
    override val displayName = "Japan Meteorological Agency"
    override val supportedRegions = setOf("JP")

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        return try {
            val response = jmaApi.getAlerts()
            val alerts = response.entries.mapNotNull { entry ->
                val title = entry.title ?: return@mapNotNull null
                // JMA entries that are not weather warnings may lack severity
                WeatherAlert(
                    id = entry.id ?: "${sourceId}_${title}_${entry.updated}",
                    event = title,
                    headline = title,
                    description = entry.content ?: "",
                    instruction = null,
                    severity = AlertSeverity.from(entry.severity),
                    urgency = AlertUrgency.from(entry.urgency),
                    certainty = entry.certainty ?: "Unknown",
                    senderName = entry.author ?: displayName,
                    areaDescription = entry.area ?: "Japan",
                    effective = entry.onset ?: entry.updated,
                    expires = entry.expires,
                    response = null,
                )
            }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
