package com.sysadmindoc.nimbus.ui.screen.radar

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

    private fun weatherAlert(id: String, geometry: AlertGeometry?): WeatherAlert {
        return WeatherAlert(
            id = id,
            event = "Tornado Warning",
            headline = "Tornado Warning issued",
            description = "Take shelter now.",
            instruction = "Move to an interior room.",
            severity = AlertSeverity.SEVERE,
            urgency = AlertUrgency.IMMEDIATE,
            certainty = "Observed",
            senderName = "National Weather Service",
            areaDescription = "Test County",
            effective = "2026-06-17T00:00:00Z",
            expires = "2099-01-01T00:00:00Z",
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
