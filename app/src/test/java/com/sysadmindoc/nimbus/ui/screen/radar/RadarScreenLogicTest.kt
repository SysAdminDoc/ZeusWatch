package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.repository.RadarProvider
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarScreenLogicTest {

    @Test
    fun `canOpenCommunityReport blocks offline and unresolved placeholder coordinates`() {
        assertFalse(canOpenCommunityReport(isOffline = true, latitude = 39.7, longitude = -104.9))
        assertFalse(canOpenCommunityReport(isOffline = false, latitude = 0.0, longitude = 0.0))
        assertTrue(canOpenCommunityReport(isOffline = false, latitude = 39.7, longitude = -104.9))
    }

    @Test
    fun `shouldShowRadarLoadingOverlay only shows for native radar without frames`() {
        val state = RadarUiState(isLoading = true, frameSet = null, error = null)

        assertTrue(
            shouldShowRadarLoadingOverlay(
                provider = RadarProvider.NATIVE_MAPLIBRE,
                selectedLayer = RadarLayer.RADAR,
                radarState = state,
                isOffline = false,
            )
        )
        assertFalse(
            shouldShowRadarLoadingOverlay(
                provider = RadarProvider.WINDY_WEBVIEW,
                selectedLayer = RadarLayer.RADAR,
                radarState = state,
                isOffline = false,
            )
        )
        assertFalse(
            shouldShowRadarLoadingOverlay(
                provider = RadarProvider.NWS_WEBVIEW,
                selectedLayer = RadarLayer.RADAR,
                radarState = state,
                isOffline = false,
            )
        )
    }

    @Test
    fun `shouldShowRadarErrorOverlay only shows for native radar failures without cached frames`() {
        assertTrue(
            shouldShowRadarErrorOverlay(
                provider = RadarProvider.NATIVE_MAPLIBRE,
                selectedLayer = RadarLayer.RADAR,
                radarState = RadarUiState(isLoading = false, frameSet = null, error = "Network error"),
                isOffline = false,
            )
        )
        assertFalse(
            shouldShowRadarErrorOverlay(
                provider = RadarProvider.NATIVE_MAPLIBRE,
                selectedLayer = RadarLayer.RADAR,
                radarState = RadarUiState(
                    isLoading = false,
                    frameSet = frameSet(),
                    error = "Network error",
                ),
                isOffline = false,
            )
        )
        assertFalse(
            shouldShowRadarErrorOverlay(
                provider = RadarProvider.NWS_STANDARD_WEBVIEW,
                selectedLayer = RadarLayer.RADAR,
                radarState = RadarUiState(isLoading = false, frameSet = null, error = "Network error"),
                isOffline = false,
            )
        )
    }

    @Test
    fun `radarFabBottomPadding lifts the report button above playback controls`() {
        assertEquals(16.dp, radarFabBottomPadding(showPlaybackControls = false))
        assertEquals(124.dp, radarFabBottomPadding(showPlaybackControls = true))
    }

    @Test
    fun `radarTopControlsSpacing keeps extra clearance when the back button is present`() {
        assertEquals(8.dp, radarTopControlsSpacing(showBackButton = false))
        assertEquals(10.dp, radarTopControlsSpacing(showBackButton = true))
    }

    private fun frameSet() = com.sysadmindoc.nimbus.data.repository.RadarFrameSet(
        past = listOf(
            com.sysadmindoc.nimbus.data.repository.TimedTileUrl(
                timestamp = 1_000L,
                tileUrl = "https://example.com/0",
                isPast = true,
            )
        ),
        forecast = emptyList(),
    )
}
