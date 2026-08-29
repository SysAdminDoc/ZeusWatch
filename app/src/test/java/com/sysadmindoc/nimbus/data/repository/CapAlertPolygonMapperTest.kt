package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.CapAlertCollection
import com.sysadmindoc.nimbus.data.api.CapAlertFeature
import com.sysadmindoc.nimbus.data.api.CapAlertGeometry
import com.sysadmindoc.nimbus.data.api.CapAlertProperties
import com.sysadmindoc.nimbus.data.model.AlertCoordinate
import com.sysadmindoc.nimbus.data.model.AlertGeometry
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * LibreWXR's /v2/alerts collection mixes Polygon and MultiPolygon features in
 * one response (703 global features when this was written), so the parser has
 * to handle both nesting depths. Geometry that silently drops turns into
 * alerts that never draw, which looks exactly like "no alerts here".
 */
class CapAlertPolygonMapperTest {

    private val now = Instant.parse("2026-08-29T12:00:00Z")

    @Test
    fun `a Polygon feature becomes one drawable ring`() {
        val alerts = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "Polygon", coordinates = SQUARE_POLYGON)),
            now,
        )

        val geometry = assertNotNull(alerts.single().geometry).let { alerts.single().geometry!! }
        assertEquals(1, geometry.polygons.size)
        assertEquals(4, geometry.polygons.single().points.size)
        assertEquals(
            AlertCoordinate(latitude = 50.0, longitude = 10.0),
            geometry.polygons.single().points.first(),
        )
    }

    @Test
    fun `a MultiPolygon feature keeps every part`() {
        val alerts = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "MultiPolygon", coordinates = TWO_PART_MULTIPOLYGON)),
            now,
        )

        // Dropping the second part would erase half a warning area from the map.
        assertEquals(2, alerts.single().geometry!!.polygons.size)
    }

    @Test
    fun `interior rings are dropped rather than drawn as solid overlays`() {
        val alerts = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "Polygon", coordinates = POLYGON_WITH_HOLE)),
            now,
        )

        assertEquals(1, alerts.single().geometry!!.polygons.size)
    }

    @Test
    fun `an unsupported geometry type yields no alert`() {
        val alerts = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "LineString", coordinates = SQUARE_POLYGON)),
            now,
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `a ring with fewer than three points yields no alert`() {
        val alerts = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "Polygon", coordinates = """[[[10.0,50.0],[11.0,50.0]]]""")),
            now,
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `expired alerts are filtered out`() {
        val expired = feature(
            type = "Polygon",
            coordinates = SQUARE_POLYGON,
            expires = Instant.parse("2026-08-29T11:00:00Z").epochSecond,
        )
        val live = feature(
            type = "Polygon",
            coordinates = SQUARE_POLYGON,
            uri = "https://example.test/live",
            expires = Instant.parse("2026-08-29T18:00:00Z").epochSecond,
        )

        val alerts = CapAlertPolygonMapper.toAlerts(collection(expired, live), now)

        assertEquals("https://example.test/live", alerts.single().id)
    }

    @Test
    fun `severity drives the ordering the map draws in`() {
        val moderate = feature(type = "Polygon", coordinates = SQUARE_POLYGON, severity = "Moderate")
        val extreme = feature(
            type = "Polygon",
            coordinates = SQUARE_POLYGON,
            severity = "Extreme",
            uri = "https://example.test/extreme",
        )

        val alerts = CapAlertPolygonMapper.toAlerts(collection(moderate, extreme), now)

        assertEquals(AlertSeverity.EXTREME, alerts.first().severity)
    }

    @Test
    fun `a provider alert matching an app alert by CAP uri is dropped`() {
        val appAlert = appAlert(id = "https://example.test/cap-1", event = "Thunderstorms")
        val provider = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "Polygon", coordinates = SQUARE_POLYGON, uri = "https://example.test/cap-1")),
            now,
        )

        val merged = CapAlertPolygonMapper.merge(listOf(appAlert), provider)

        // The app's copy carries the full description and instruction; drawing
        // both would stack two identical shapes on the map.
        assertEquals(listOf(appAlert), merged)
    }

    @Test
    fun `a provider alert matching by event and area is dropped even without a uri`() {
        val appAlert = appAlert(id = "nws-123", event = "Thunderstorms", area = "Mogilev Region")
        val provider = CapAlertPolygonMapper.toAlerts(
            collection(
                feature(
                    type = "Polygon",
                    coordinates = SQUARE_POLYGON,
                    title = "thunderstorms",
                    regions = "  Mogilev Region ",
                    uri = "",
                ),
            ),
            now,
        )

        // Not every source exposes the CAP uri, so the event/area pair is the
        // fallback identity.
        assertEquals(listOf(appAlert), CapAlertPolygonMapper.merge(listOf(appAlert), provider))
    }

    @Test
    fun `a provider alert the app never saw is added`() {
        val appAlert = appAlert(id = "nws-123", event = "Flood Warning", area = "Kent")
        val provider = CapAlertPolygonMapper.toAlerts(
            collection(feature(type = "Polygon", coordinates = SQUARE_POLYGON, title = "Thunderstorms")),
            now,
        )

        val merged = CapAlertPolygonMapper.merge(listOf(appAlert), provider)

        // This is the whole point: MeteoAlarm and friends publish no geometry,
        // so without the provider polygon nothing would draw at all.
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.event == "Thunderstorms" && it.geometry != null })
    }

    private fun collection(vararg features: CapAlertFeature) =
        CapAlertCollection(features = features.toList())

    private fun feature(
        type: String,
        coordinates: String,
        title: String = "Thunderstorms",
        severity: String = "Moderate",
        regions: String = "Mogilev Region",
        uri: String = "https://example.test/cap-1",
        expires: Long = Instant.parse("2026-08-29T18:00:00Z").epochSecond,
    ) = CapAlertFeature(
        geometry = CapAlertGeometry(type = type, coordinates = json(coordinates)),
        properties = CapAlertProperties(
            title = title,
            severity = severity,
            time = Instant.parse("2026-08-29T09:00:00Z").epochSecond,
            expires = expires,
            description = title,
            regions = regions,
            uri = uri,
        ),
    )

    private fun appAlert(
        id: String,
        event: String,
        area: String = "Mogilev Region",
    ) = WeatherAlert(
        id = id,
        event = event,
        headline = event,
        description = event,
        instruction = null,
        severity = AlertSeverity.MODERATE,
        urgency = AlertUrgency.EXPECTED,
        certainty = "Likely",
        senderName = "Test",
        areaDescription = area,
        effective = null,
        expires = null,
        response = null,
        geometry = AlertGeometry(listOf(AlertPolygon(SQUARE_POINTS))),
    )

    private fun json(raw: String): JsonElement = Json.parseToJsonElement(raw)

    private companion object {
        const val SQUARE_POLYGON = """[[[10.0,50.0],[11.0,50.0],[11.0,51.0],[10.0,51.0]]]"""
        const val POLYGON_WITH_HOLE =
            """[[[10.0,50.0],[11.0,50.0],[11.0,51.0],[10.0,51.0]],""" +
                """[[10.4,50.4],[10.6,50.4],[10.6,50.6],[10.4,50.6]]]"""
        const val TWO_PART_MULTIPOLYGON =
            """[[[[10.0,50.0],[11.0,50.0],[11.0,51.0],[10.0,51.0]]],""" +
                """[[[20.0,60.0],[21.0,60.0],[21.0,61.0],[20.0,61.0]]]]"""

        val SQUARE_POINTS = listOf(
            AlertCoordinate(50.0, 10.0),
            AlertCoordinate(50.0, 11.0),
            AlertCoordinate(51.0, 11.0),
            AlertCoordinate(51.0, 10.0),
        )
    }
}
