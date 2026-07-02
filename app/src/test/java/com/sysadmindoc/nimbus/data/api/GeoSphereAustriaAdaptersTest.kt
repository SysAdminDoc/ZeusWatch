package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GeoSphereAustriaNowcastAdapterTest {
    private val api = mockk<GeoSphereAustriaDatasetApi>()
    private val adapter = GeoSphereAustriaNowcastAdapter(api)

    @Test
    fun mapsIncaPrecipitationBuckets() = runTest {
        coEvery {
            api.getIncaNowcast("48.208200,16.373800", "rr", 0)
        } returns GeoSphereNowcastResponse(
            timestamps = listOf(
                "2026-07-02T10:15+00:00",
                "2026-07-02T10:30+00:00",
                "2026-07-02T10:45+00:00",
            ),
            features = listOf(
                GeoSphereNowcastFeature(
                    properties = GeoSphereNowcastProperties(
                        parameters = GeoSphereNowcastParameters(
                            rr = GeoSphereNowcastSeries(data = listOf(0.0, 0.4, null)),
                        ),
                    ),
                ),
            ),
        )

        val result = adapter.getMinutelyPrecipitation(48.2082, 16.3738).getOrThrow()

        assertEquals(3, result.size)
        assertEquals(LocalDateTime.of(2026, 7, 2, 10, 15), result[0].time)
        assertEquals(0.0, result[0].precipitation, 0.0)
        assertEquals(0.4, result[1].precipitation, 0.0)
        assertEquals(0.0, result[2].precipitation, 0.0)
    }

    @Test
    fun skipsNowcastOutsideCoverage() = runTest {
        val result = adapter.getMinutelyPrecipitation(40.7128, -74.0060)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify(exactly = 0) {
            api.getIncaNowcast(any(), any(), any())
        }
    }
}

class GeoSphereAustriaAlertAdapterTest {
    private val api = mockk<GeoSphereAustriaWarnApi>()
    private val adapter = GeoSphereAustriaAlertAdapter(api)

    @Test
    fun mapsPointWarningsToWeatherAlerts() = runTest {
        val begin = LocalDateTime.now()
            .plusHours(2)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        coEvery {
            api.getWarningsForCoords(longitude = 16.3738, latitude = 48.2082, language = "en")
        } returns GeoSphereWarningResponse(
            properties = GeoSphereWarningProperties(
                location = GeoSphereWarningLocation(
                    properties = GeoSphereWarningLocationProperties(name = "Wien-Innere Stadt"),
                ),
                warnings = listOf(
                    GeoSphereWarningObject(
                        warnid = JsonPrimitive(10001),
                        warningLevel = 2,
                        warningType = 2,
                        begin = begin,
                        end = "2026-07-02T18:00:00+00:00",
                        text = "Orange Rain Warning\n",
                        impacts = "Heavy rainfall is expected.",
                        recommendations = "Avoid flooded underpasses.",
                    ),
                ),
            ),
        )

        val alert = adapter.getAlerts(48.2082, 16.3738).getOrThrow().single()

        assertEquals("10001", alert.id)
        assertEquals("Rain", alert.event)
        assertEquals("Orange Rain Warning", alert.headline)
        assertEquals(AlertSeverity.MODERATE, alert.severity)
        assertEquals("GeoSphere Austria", alert.senderName)
        assertEquals("Wien-Innere Stadt", alert.areaDescription)
        assertEquals("Avoid flooded underpasses.", alert.instruction)
        assertEquals(true, alert.coversRequestedLocation)
    }

    @Test
    fun skipsWarningsOutsideAustriaCoverage() = runTest {
        val result = adapter.getAlerts(40.7128, -74.0060)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        coVerify(exactly = 0) {
            api.getWarningsForCoords(any(), any(), any())
        }
    }
}
