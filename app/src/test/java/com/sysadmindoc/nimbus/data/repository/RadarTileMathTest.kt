package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarTileMathTest {

    @Test
    fun `tile at equator prime meridian is center of the grid`() {
        val (x, y) = latLonToTile(0.0, 0.0, zoom = 6)
        assertEquals(32, x)
        assertEquals(32, y)
    }

    @Test
    fun `tile for Oslo at zoom 6`() {
        val (x, y) = latLonToTile(59.91, 10.75, zoom = 6)
        assertEquals(33, x)
        assertEquals(18, y)
    }

    @Test
    fun `tile for New York at zoom 6`() {
        val (x, y) = latLonToTile(40.71, -74.01, zoom = 6)
        assertEquals(18, x)
        assertEquals(24, y)
    }

    @Test
    fun `extreme latitude is clamped`() {
        val (_, y) = latLonToTile(90.0, 0.0, zoom = 6)
        assertEquals(0, y)
        val (_, y2) = latLonToTile(-90.0, 0.0, zoom = 6)
        assertEquals(63, y2)
    }

    @Test
    fun `buildPreviewTileUrls substitutes placeholders`() {
        val repo = RadarRepository(radarApiStub())
        val urls = repo.buildPreviewTileUrls(
            lat = 51.5,
            lon = -0.12,
            tileUrlTemplate = "https://tiles.example.com/{z}/{x}/{y}/radar.png",
        )
        assert(urls.radarTileUrl.contains("/6/")) { "zoom should be 6" }
        assert(!urls.radarTileUrl.contains("{z}")) { "template not substituted" }
        assert(urls.baseMapUrl.contains("basemaps.cartocdn.com"))
    }

    @Test
    fun `buildPreviewTileUrls clamps preview zoom to RainViewer public max`() {
        val repo = RadarRepository(radarApiStub())
        val urls = repo.buildPreviewTileUrls(
            lat = 51.5,
            lon = -0.12,
            tileUrlTemplate = "https://tiles.example.com/{z}/{x}/{y}/radar.png",
            zoom = 12,
        )

        assert(urls.radarTileUrl.contains("/7/")) { "RainViewer public tiles should not exceed zoom 7" }
        assert(urls.baseMapUrl.contains("/7/")) { "preview basemap should use the clamped zoom" }
    }

    private fun radarApiStub() = io.mockk.mockk<com.sysadmindoc.nimbus.data.api.RainViewerApi>()
}
