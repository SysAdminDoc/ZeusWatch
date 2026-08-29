package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.CapAlertCollection
import com.sysadmindoc.nimbus.data.api.CapAlertFeature
import com.sysadmindoc.nimbus.data.model.AlertCoordinate
import com.sysadmindoc.nimbus.data.model.AlertGeometry
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val CAP_SENDER = "WMO CAP via LibreWXR"
private const val MIN_RING_POINTS = 3

/**
 * Turns LibreWXR's CAP GeoJSON into the app's [WeatherAlert] shape so the
 * existing radar polygon layer can draw it without knowing where it came from.
 */
object CapAlertPolygonMapper {

    /** Alerts that carry a usable polygon and have not expired at [now]. */
    fun toAlerts(collection: CapAlertCollection, now: Instant): List<WeatherAlert> =
        collection.features
            .mapNotNull { feature -> toAlert(feature) }
            .filter { alert -> alert.expires?.let { Instant.parse(it) }?.isAfter(now) ?: true }
            .sortedBy { it.severity.sortOrder }

    /**
     * Merges provider polygons into the app's own alerts.
     *
     * The app's alerts win: they carry the full description, instruction and
     * localized text, and a provider polygon for the same CAP alert would draw
     * the same shape twice with the darker one on top. Matching is by CAP URI
     * first, then by the event/area pair, because not every source exposes the
     * URI the CAP feed used.
     */
    fun merge(appAlerts: List<WeatherAlert>, providerAlerts: List<WeatherAlert>): List<WeatherAlert> {
        val seenUris = appAlerts.mapNotNullTo(mutableSetOf()) { it.capUriOrNull() }
        val seenEvents = appAlerts.mapTo(mutableSetOf()) { it.dedupeKey() }
        val extra = providerAlerts.filter { candidate ->
            val uri = candidate.capUriOrNull()
            (uri == null || uri !in seenUris) && candidate.dedupeKey() !in seenEvents
        }
        return (appAlerts + extra).sortedBy { it.severity.sortOrder }
    }

    private fun toAlert(feature: CapAlertFeature): WeatherAlert? {
        val polygons = feature.geometry?.let { parsePolygons(it.type, it.coordinates) }.orEmpty()
        if (polygons.isEmpty()) return null
        val properties = feature.properties
        val title = properties.title.ifBlank { properties.description }.ifBlank { return null }
        return WeatherAlert(
            // The CAP URI is unique per alert; fall back to the shape's identity
            // so two alerts never collapse into one map feature.
            id = properties.uri.ifBlank { "cap:$title:${properties.time}:${properties.regions}" },
            event = title,
            headline = title,
            description = properties.description,
            instruction = null,
            severity = AlertSeverity.from(properties.severity),
            urgency = AlertUrgency.UNKNOWN,
            certainty = "",
            senderName = CAP_SENDER,
            areaDescription = properties.regions,
            effective = properties.time.takeIf { it > 0 }?.toIsoInstant(),
            expires = properties.expires.takeIf { it > 0 }?.toIsoInstant(),
            response = null,
            geometry = AlertGeometry(polygons),
        )
    }

    /**
     * The endpoint mixes `Polygon` and `MultiPolygon` in one collection, so the
     * nesting depth differs per feature. Interior rings are dropped: the map
     * layer draws filled shapes and a hole would render as a solid overlay.
     */
    internal fun parsePolygons(type: String, coordinates: JsonElement?): List<AlertPolygon> {
        val array = coordinates as? JsonArray ?: return emptyList()
        return when (type) {
            "Polygon" -> listOfNotNull(array.firstOrNull()?.let(::parseRing))
            "MultiPolygon" -> array.mapNotNull { polygon ->
                (polygon as? JsonArray)?.firstOrNull()?.let(::parseRing)
            }
            else -> emptyList()
        }
    }

    private fun parseRing(ring: JsonElement): AlertPolygon? {
        val points = (ring as? JsonArray)?.mapNotNull { point ->
            val pair = point as? JsonArray ?: return@mapNotNull null
            val lon = (pair.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (pair.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            AlertCoordinate(latitude = lat, longitude = lon)
        }.orEmpty()
        return if (points.size >= MIN_RING_POINTS) AlertPolygon(points) else null
    }

    private fun WeatherAlert.capUriOrNull(): String? = id.takeIf { it.startsWith("http") }

    private fun WeatherAlert.dedupeKey(): String =
        "${event.trim().lowercase()}|${areaDescription.trim().lowercase()}"

    private fun Long.toIsoInstant(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(this).atOffset(ZoneOffset.UTC))
}
