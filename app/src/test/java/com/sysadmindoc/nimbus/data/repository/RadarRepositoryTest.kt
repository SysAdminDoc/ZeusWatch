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
            ),
            FakeRainViewerApi(emptyResponse()),
        )

        val frameSet = repository.getRadarFrames().getOrThrow()

        assertEquals(RadarTileSource.RAINVIEWER, frameSet.source)
        assertEquals(RainViewerApi.MAX_TILE_ZOOM, frameSet.maxTileZoom)
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
            ),
            FakeRainViewerApi(emptyResponse()),
        )

        val result = repository.getRadarFrames()

        assertTrue(result.isFailure)
    }

    @Test
    fun `LibreWXR frames use higher fidelity tiles and nowcast playback`() = runTest {
        val repository = RadarRepository(
            FakeRainViewerApi(emptyResponse()),
            FakeRainViewerApi(
                RainViewerResponse(
                    host = "https://api.librewxr.net",
                    radar = RadarData(
                        past = listOf(RadarFrame(time = 100L, path = "/v2/radar/100")),
                        nowcast = listOf(RadarFrame(time = 160L, path = "/v2/radar/160")),
                    ),
                )
            ),
        )

        val frameSet = repository.getRadarFrames(RadarProvider.LIBREWXR_NATIVE).getOrThrow()

        assertEquals(RadarTileSource.LIBREWXR, frameSet.source)
        assertEquals(RainViewerApi.LIBREWXR_MAX_TILE_ZOOM, frameSet.maxTileZoom)
        assertEquals(1, frameSet.past.size)
        assertEquals(1, frameSet.forecast.size)
        assertTrue(frameSet.past.first().isPast)
        assertFalse(frameSet.forecast.first().isPast)
        assertTrue(frameSet.past.first().tileUrl.startsWith("https://api.librewxr.net/v2/radar/"))
        assertTrue(frameSet.past.first().tileUrl.endsWith("/10/1_1.png"))
    }

    @Test
    fun `LibreWXR provider falls back to RainViewer when primary fails`() = runTest {
        val repository = RadarRepository(
            FakeRainViewerApi(
                RainViewerResponse(
                    host = "https://tiles.example.com",
                    radar = RadarData(
                        past = listOf(RadarFrame(time = 100L, path = "/v2/radar/100")),
                    ),
                )
            ),
            FakeRainViewerApi(error = IllegalStateException("LibreWXR down")),
        )

        val frameSet = repository.getRadarFrames(RadarProvider.LIBREWXR_NATIVE).getOrThrow()

        assertEquals(RadarTileSource.RAINVIEWER, frameSet.source)
        assertEquals(RainViewerApi.MAX_TILE_ZOOM, frameSet.maxTileZoom)
        assertEquals(1, frameSet.past.size)
        assertTrue(frameSet.past.first().tileUrl.endsWith("/2/1_1.png"))
    }

    @Test
    fun `cacheOnly returns cached frame metadata without calling network`() = runTest {
        val cache = FakeRadarFrameMetadataCache(
            cached = frameSet(source = RadarTileSource.RAINVIEWER, isFromCache = true)
        )
        val repository = RadarRepository(
            FakeRainViewerApi(error = AssertionError("network should not be called")),
            FakeRainViewerApi(error = AssertionError("network should not be called")),
            cache,
        )

        val frameSet = repository.getRadarFrames(cacheOnly = true).getOrThrow()

        assertTrue(frameSet.isFromCache)
        assertEquals(RadarTileSource.RAINVIEWER, frameSet.source)
        assertEquals("https://tiles.example.com/v2/radar/100/512/{z}/{x}/{y}/2/1_1.png", frameSet.past.first().tileUrl)
    }

    @Test
    fun `getRadarFrames falls back to cached metadata when live request fails`() = runTest {
        val cache = FakeRadarFrameMetadataCache(
            cached = frameSet(source = RadarTileSource.LIBREWXR, isFromCache = true)
        )
        val repository = RadarRepository(
            FakeRainViewerApi(error = IllegalStateException("RainViewer down")),
            FakeRainViewerApi(error = IllegalStateException("LibreWXR down")),
            cache,
        )

        val frameSet = repository.getRadarFrames(RadarProvider.LIBREWXR_NATIVE).getOrThrow()

        assertTrue(frameSet.isFromCache)
        assertEquals(RadarTileSource.LIBREWXR, frameSet.source)
    }

    @Test
    fun `cached radar frame metadata expires after offline fallback window`() {
        val cached = frameSet(source = RadarTileSource.RAINVIEWER)
            .toCachedRadarFrameSet(cachedAtMillis = 1_000L)

        assertTrue(cached.toFrameSetIfFresh(nowMillis = 1_000L + 6 * 60 * 60 * 1000L) != null)
        assertEquals(null, cached.toFrameSetIfFresh(nowMillis = 1_000L + 6 * 60 * 60 * 1000L + 1L))
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
    fun `buildTileUrl can keep LibreWXR color schemes when RainViewer restriction is disabled`() {
        val tileUrl = RainViewerApi.buildTileUrl(
            path = "/v2/radar/100",
            host = "https://api.librewxr.net",
            colorScheme = RainViewerApi.LIBREWXR_VIPER_HD_COLOR_SCHEME,
            restrictToPublicRainViewer = false,
        )

        assertEquals("https://api.librewxr.net/v2/radar/100/512/{z}/{x}/{y}/10/1_1.png", tileUrl)
    }

    @Test
    fun `RainViewer public policy documents degraded tile tier`() {
        assertEquals(7, RainViewerApi.MAX_TILE_ZOOM)
        assertEquals(12, RainViewerApi.LIBREWXR_MAX_TILE_ZOOM)
        assertEquals(2, RainViewerApi.UNIVERSAL_BLUE_COLOR_SCHEME)
        assertEquals("png", RainViewerApi.PUBLIC_TILE_FORMAT)
        assertFalse(RainViewerApi.SUPPORTS_PUBLIC_NOWCAST_TILES)
        assertFalse(RainViewerApi.SUPPORTS_PUBLIC_SATELLITE_TILES)
    }

    private class FakeRainViewerApi(
        private val response: RainViewerResponse? = null,
        private val error: Throwable? = null,
    ) : RainViewerApi {
        override suspend fun getWeatherMaps(): RainViewerResponse {
            error?.let { throw it }
            return requireNotNull(response)
        }
    }

    private class FakeRadarFrameMetadataCache(
        private var cached: RadarFrameSet? = null,
    ) : RadarFrameMetadataCache {
        override fun read(provider: RadarProvider): RadarFrameSet? = cached

        override fun write(provider: RadarProvider, frameSet: RadarFrameSet) {
            cached = frameSet
        }
    }

    private fun emptyResponse() = RainViewerResponse(
        host = "https://tiles.example.com",
        radar = RadarData(past = emptyList()),
    )

    private fun frameSet(
        source: RadarTileSource,
        isFromCache: Boolean = false,
    ) = RadarFrameSet(
        past = listOf(
            TimedTileUrl(
                timestamp = 100L,
                tileUrl = "https://tiles.example.com/v2/radar/100/512/{z}/{x}/{y}/2/1_1.png",
                isPast = true,
            )
        ),
        forecast = emptyList(),
        source = source,
        maxTileZoom = if (source == RadarTileSource.LIBREWXR) {
            RainViewerApi.LIBREWXR_MAX_TILE_ZOOM
        } else {
            RainViewerApi.MAX_TILE_ZOOM
        },
        isFromCache = isFromCache,
        cachedAtMillis = if (isFromCache) 1_000L else null,
    )
}
