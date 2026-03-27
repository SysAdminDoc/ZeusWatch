package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlertSourceAdapter] for Environment and Climate Change Canada (ECCC).
 * Covers Canada (CA) only.
 *
 * ECCC publishes alerts via CAP-CP feeds organized by province.
 * This adapter takes lat/lon, maps to a Canadian province code,
 * then fetches alerts for that province.
 */
@Singleton
class EnvironmentCanadaAlertAdapter @Inject constructor(
    private val ecApi: EnvironmentCanadaAlertApi,
) : AlertSourceAdapter {

    override val sourceId = "eccc"
    override val displayName = "Environment Canada"
    override val supportedRegions = setOf("CA")

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        return try {
            val province = resolveProvince(lat, lon)
            if (province == null) {
                return Result.success(emptyList())
            }
            val response = ecApi.getProvinceAlerts(province)
            val alerts = response.entries.mapNotNull { entry ->
                val title = entry.title ?: return@mapNotNull null
                // Skip non-alert entries (e.g. "No watches or warnings in effect")
                if (title.contains("No watches", ignoreCase = true) ||
                    title.contains("No warnings", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }
                WeatherAlert(
                    id = entry.id ?: "${sourceId}_${title}_${entry.updated}",
                    event = title,
                    headline = title,
                    description = entry.summary ?: "",
                    instruction = null,
                    severity = AlertSeverity.from(entry.severity),
                    urgency = AlertUrgency.from(entry.urgency),
                    certainty = entry.certainty ?: "Unknown",
                    senderName = displayName,
                    areaDescription = entry.areaDesc ?: province.uppercase(),
                    effective = entry.effective ?: entry.updated,
                    expires = entry.expires,
                    response = null,
                )
            }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rough province resolution from lat/lon using bounding boxes.
     * Returns a two-letter province code (lowercase) or null.
     */
    internal fun resolveProvince(lat: Double, lon: Double): String? {
        // Approximate bounding boxes for Canadian provinces
        return PROVINCE_BOUNDS.entries.firstOrNull { (_, bounds) ->
            lat in bounds.latRange && lon in bounds.lonRange
        }?.key
    }

    private data class ProvinceBounds(
        val latRange: ClosedFloatingPointRange<Double>,
        val lonRange: ClosedFloatingPointRange<Double>,
    )

    companion object {
        // Coarse bounding boxes — good enough for province-level resolution.
        // Overlaps are resolved by first-match (ordered west to east).
        private val PROVINCE_BOUNDS = linkedMapOf(
            "bc" to ProvinceBounds(48.3..60.0, -139.1..-114.0),
            "ab" to ProvinceBounds(49.0..60.0, -120.0..-110.0),
            "sk" to ProvinceBounds(49.0..60.0, -110.0..-101.4),
            "mb" to ProvinceBounds(49.0..60.0, -102.0..-88.9),
            "on" to ProvinceBounds(41.7..56.9, -95.2..-74.3),
            "qc" to ProvinceBounds(45.0..62.6, -79.8..-57.1),
            "nb" to ProvinceBounds(44.6..48.1, -69.1..-63.8),
            "ns" to ProvinceBounds(43.4..47.0, -66.4..-59.7),
            "pe" to ProvinceBounds(45.9..47.1, -64.4..-62.0),
            "nl" to ProvinceBounds(46.6..60.4, -67.8..-52.6),
            "yt" to ProvinceBounds(60.0..69.6, -141.0..-124.0),
            "nt" to ProvinceBounds(60.0..78.8, -136.5..-102.0),
            "nu" to ProvinceBounds(51.7..83.1, -120.4..-61.2),
        )
    }
}
