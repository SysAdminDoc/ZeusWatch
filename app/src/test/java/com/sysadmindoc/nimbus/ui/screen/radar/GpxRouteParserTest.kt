package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.repository.DrivingRouteEstimateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class GpxRouteParserTest {
    private val parser = GpxRouteParser()

    @Test
    fun `parses GPX 1_1 route points in document order`() {
        val geometry = parser.parse(
            gpx(
                """
                <rte>
                  <rtept lat="39.7" lon="-105.0" />
                  <rtept lat="40.0" lon="-105.2" />
                </rte>
                """.trimIndent()
            ).byteInputStream()
        )

        assertEquals(DrivingRouteEstimateKind.GPX_ROUTE, geometry.estimateKind)
        assertEquals(2, geometry.points.size)
        assertEquals(40.0, geometry.points.last().latitude, 0.0)
    }

    @Test
    fun `parses GPX 1_1 track when no route exists`() {
        val geometry = parser.parse(
            gpx(
                """
                <trk><trkseg>
                  <trkpt lat="39.7" lon="-105.0" />
                  <trkpt lat="39.8" lon="-105.1" />
                </trkseg></trk>
                """.trimIndent()
            ).byteInputStream()
        )

        assertEquals(DrivingRouteEstimateKind.GPX_TRACK, geometry.estimateKind)
        assertEquals(2, geometry.points.size)
    }

    @Test
    fun `rejects DTD and external entity declarations`() {
        val error = assertThrows(GpxRouteParseException::class.java) {
            parser.parse(
                """
                <?xml version="1.0"?>
                <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
                  <rte><rtept lat="0" lon="0"/><rtept lat="1" lon="1"/></rte>
                </gpx>
                """.trimIndent().byteInputStream()
            )
        }

        assertEquals(GpxRouteParseFailure.UNSAFE_XML, error.reason)
    }

    @Test
    fun `rejects files larger than five megabytes`() {
        val error = assertThrows(GpxRouteParseException::class.java) {
            parser.parse(ByteArrayInputStream(ByteArray(MAX_GPX_BYTES + 1)))
        }

        assertEquals(GpxRouteParseFailure.FILE_TOO_LARGE, error.reason)
    }

    @Test
    fun `rejects more than ten thousand route points`() {
        val points = buildString {
            repeat(MAX_GPX_POINTS + 1) { index ->
                append("<rtept lat=\"0\" lon=\"")
                append((index % 180) - 90)
                append("\"/>")
            }
        }

        val error = assertThrows(GpxRouteParseException::class.java) {
            parser.parse(gpx("<rte>$points</rte>").byteInputStream())
        }

        assertEquals(GpxRouteParseFailure.TOO_MANY_POINTS, error.reason)
    }

    @Test
    fun `rejects GPX 1_0 and invalid coordinates`() {
        val wrongVersion = assertThrows(GpxRouteParseException::class.java) {
            parser.parse(
                "<gpx version=\"1.0\"><rte><rtept lat=\"0\" lon=\"0\"/><rtept lat=\"1\" lon=\"1\"/></rte></gpx>"
                    .byteInputStream()
            )
        }
        val invalidCoordinate = assertThrows(GpxRouteParseException::class.java) {
            parser.parse(
                gpx("<rte><rtept lat=\"91\" lon=\"0\"/><rtept lat=\"1\" lon=\"1\"/></rte>")
                    .byteInputStream()
            )
        }

        assertEquals(GpxRouteParseFailure.INVALID_GPX, wrongVersion.reason)
        assertEquals(GpxRouteParseFailure.INVALID_GPX, invalidCoordinate.reason)
    }

    private fun gpx(body: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n" +
            body +
            "\n</gpx>"
}
