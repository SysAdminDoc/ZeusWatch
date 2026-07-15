package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.api.LightningStrike
import com.sysadmindoc.nimbus.data.model.AlertCoordinate
import com.sysadmindoc.nimbus.data.model.AlertGeometry
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Polygon
import java.time.Instant
import kotlin.random.Random

class RadarMapViewTest {

    @Test
    fun `alertOverlayFeatureCollection converts alert polygons to tappable features`() {
        val collection = alertOverlayFeatureCollection(
            listOf(weatherAlert(id = "alert-1", geometry = alertGeometry(closed = false)))
        )

        val features = collection.features().orEmpty()
        assertEquals(1, features.size)
        val feature = features.first()
        assertEquals("alert-1", feature.getStringProperty("alertId"))
        assertEquals("Tornado Warning", feature.getStringProperty("event"))
        assertEquals("SEVERE", feature.getStringProperty("severity"))
        assertEquals("#FF5722", feature.getStringProperty("alertColor"))
        assertTrue(feature.geometry() is Polygon)

        val ring = (feature.geometry() as Polygon).coordinates().first()
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun `alertOverlayFeatureCollection drops text-only and malformed alert geometry`() {
        val collection = alertOverlayFeatureCollection(
            listOf(
                weatherAlert(id = "text-only", geometry = null),
                weatherAlert(
                    id = "malformed",
                    geometry = AlertGeometry(
                        polygons = listOf(
                            AlertPolygon(
                                points = listOf(
                                    AlertCoordinate(latitude = 39.0, longitude = -105.0),
                                    AlertCoordinate(latitude = 39.0, longitude = -104.0),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(collection.features().orEmpty().isEmpty())
    }

    @Test
    fun `alert polygons draw least severe first regardless of input order`() {
        val alerts = AlertSeverity.entries.map { severity ->
            weatherAlert(
                id = severity.name.lowercase(),
                geometry = alertGeometry(closed = false),
                severity = severity,
            )
        }
        val expectedDrawOrder = listOf("unknown", "minor", "moderate", "severe", "extreme")

        repeat(20) { seed ->
            val collection = alertOverlayFeatureCollection(alerts.shuffled(Random(seed)))

            assertEquals(
                expectedDrawOrder,
                collection.features().orEmpty().map { it.getStringProperty("alertId") },
            )
        }
    }

    @Test
    fun `overlap selection chooses highest severity active alert deterministically`() {
        val now = Instant.parse("2026-07-15T12:00:00Z")
        val alerts = listOf(
            weatherAlert(
                id = "expired-extreme",
                geometry = alertGeometry(closed = false),
                severity = AlertSeverity.EXTREME,
                expires = "2026-07-15T11:59:59Z",
            ),
            weatherAlert(
                id = "moderate",
                geometry = alertGeometry(closed = false),
                severity = AlertSeverity.MODERATE,
            ),
            weatherAlert(
                id = "severe",
                geometry = alertGeometry(closed = false),
                severity = AlertSeverity.SEVERE,
            ),
        )
        repeat(20) { seed ->
            val features = alertOverlayFeatureCollection(alerts.shuffled(Random(seed))).features().orEmpty()
            assertEquals("severe", highestPriorityActiveAlertId(features, now))
        }
        val features = alertOverlayFeatureCollection(alerts).features().orEmpty()
        val moderateFeature = features.single { it.getStringProperty("alertId") == "moderate" }
        assertEquals("moderate", highestPriorityActiveAlertId(listOf(moderateFeature), now))
    }

    @Test
    fun `lightningFeatureCollection caps GeoJSON points to latest strikes`() {
        val collection = lightningFeatureCollection(
            List(300) { index ->
                LightningStrike(
                    lat = index.toDouble(),
                    lon = -index.toDouble(),
                    timestamp = index.toLong(),
                )
            }
        )

        val features = collection.features().orEmpty()
        assertEquals(250, features.size)
        assertEquals(50.0, (features.first().geometry() as org.maplibre.geojson.Point).latitude(), 0.0)
        assertEquals(299.0, (features.last().geometry() as org.maplibre.geojson.Point).latitude(), 0.0)
    }

    private fun weatherAlert(
        id: String,
        geometry: AlertGeometry?,
        severity: AlertSeverity = AlertSeverity.SEVERE,
        expires: String = "2099-01-01T00:00:00Z",
    ): WeatherAlert {
        return WeatherAlert(
            id = id,
            event = "Tornado Warning",
            headline = "Tornado Warning issued",
            description = "Take shelter now.",
            instruction = "Move to an interior room.",
            severity = severity,
            urgency = AlertUrgency.IMMEDIATE,
            certainty = "Observed",
            senderName = "National Weather Service",
            areaDescription = "Test County",
            effective = "2026-06-17T00:00:00Z",
            expires = expires,
            response = "Shelter",
            geometry = geometry,
            coversRequestedLocation = true,
        )
    }

    private fun alertGeometry(closed: Boolean): AlertGeometry {
        val points = listOf(
            AlertCoordinate(latitude = 39.0, longitude = -105.0),
            AlertCoordinate(latitude = 39.0, longitude = -104.0),
            AlertCoordinate(latitude = 40.0, longitude = -104.0),
            AlertCoordinate(latitude = 40.0, longitude = -105.0),
        )
        return AlertGeometry(
            polygons = listOf(
                AlertPolygon(
                    points = if (closed) points + points.first() else points,
                ),
            ),
        )
    }
}
