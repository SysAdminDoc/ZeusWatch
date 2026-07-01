package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.RadarData
import com.sysadmindoc.nimbus.data.api.RadarFrame
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import com.sysadmindoc.nimbus.data.api.RainViewerResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarRepositoryTest {

    @Test
    fun `getRadarFrames uses only past RainViewer frames with supported tile options`() = runTest {
        val repository = RadarRepository(
            FakeRainViewerApi(
                RainViewerResponse(
                    host = "https://tiles.example.com",
                    radar = RadarData(
                        past = listOf(
                            RadarFrame(time = 100L, path = "/v2/radar/100"),
                            RadarFrame(time = 200L, path = "/v2/radar/200"),
                        ),
                        nowcast = listOf(RadarFrame(time = 300L, path = "/v2/radar/300")),
                    ),
                )
            )
        )

        val frameSet = repository.getRadarFrames().getOrThrow()

        assertEquals(2, frameSet.past.size)
        assertEquals(0, frameSet.forecast.size)
        assertEquals(2, frameSet.totalFrames)
        assertTrue(frameSet.past.all { it.isPast })
        assertTrue(frameSet.past.all { it.tileUrl.startsWith("https://tiles.example.com/v2/radar/") })
        assertTrue(frameSet.past.all { it.tileUrl.endsWith("/2/1_1.png") })
        assertFalse(frameSet.past.any { it.tileUrl.contains("/4/") })
    }

    @Test
    fun `getRadarFrames fails when only discontinued nowcast frames are returned`() = runTest {
        val repository = RadarRepository(
            FakeRainViewerApi(
                RainViewerResponse(
                    host = "https://tiles.example.com",
                    radar = RadarData(
                        past = emptyList(),
                        nowcast = listOf(RadarFrame(time = 300L, path = "/v2/radar/300")),
                    ),
                )
            )
        )

        val result = repository.getRadarFrames()

        assertTrue(result.isFailure)
    }

    @Test
    fun `buildTileUrl forces unsupported color schemes back to Universal Blue`() {
        val tileUrl = RainViewerApi.buildTileUrl(
            path = "/v2/radar/100",
            host = "https://tiles.example.com",
            colorScheme = 4,
        )

        assertEquals("https://tiles.example.com/v2/radar/100/512/{z}/{x}/{y}/2/1_1.png", tileUrl)
    }

    @Test
    fun `RainViewer public policy documents degraded tile tier`() {
        assertEquals(7, RainViewerApi.MAX_TILE_ZOOM)
        assertEquals(2, RainViewerApi.UNIVERSAL_BLUE_COLOR_SCHEME)
        assertEquals("png", RainViewerApi.PUBLIC_TILE_FORMAT)
        assertFalse(RainViewerApi.SUPPORTS_PUBLIC_NOWCAST_TILES)
        assertFalse(RainViewerApi.SUPPORTS_PUBLIC_SATELLITE_TILES)
    }

    private class FakeRainViewerApi(
        private val response: RainViewerResponse,
    ) : RainViewerApi {
        override suspend fun getWeatherMaps(): RainViewerResponse = response
    }
}
