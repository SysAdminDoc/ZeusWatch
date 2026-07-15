package com.sysadmindoc.nimbus.ui.screen.radar

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarPlaybackControlsTest {
    @Test
    fun `display value follows frame index in ltr`() {
        assertEquals(0f, radarPlaybackDisplayValue(frame = 0, totalFrames = 5, isRtl = false))
        assertEquals(2f, radarPlaybackDisplayValue(frame = 2, totalFrames = 5, isRtl = false))
        assertEquals(4f, radarPlaybackDisplayValue(frame = 4, totalFrames = 5, isRtl = false))
    }

    @Test
    fun `display value mirrors frame index in rtl`() {
        assertEquals(4f, radarPlaybackDisplayValue(frame = 0, totalFrames = 5, isRtl = true))
        assertEquals(2f, radarPlaybackDisplayValue(frame = 2, totalFrames = 5, isRtl = true))
        assertEquals(0f, radarPlaybackDisplayValue(frame = 4, totalFrames = 5, isRtl = true))
    }

    @Test
    fun `display value clamps invalid frame and empty frame sets`() {
        assertEquals(0f, radarPlaybackDisplayValue(frame = 8, totalFrames = 0, isRtl = false))
        assertEquals(0f, radarPlaybackDisplayValue(frame = 8, totalFrames = 0, isRtl = true))
        assertEquals(4f, radarPlaybackDisplayValue(frame = 8, totalFrames = 5, isRtl = false))
        assertEquals(0f, radarPlaybackDisplayValue(frame = 8, totalFrames = 5, isRtl = true))
    }

    @Test
    fun `display seek rounds to the nearest frame index`() {
        assertEquals(4, radarPlaybackFrameFromDisplayValue(displayValue = 3.9f, totalFrames = 5, isRtl = false))
        assertEquals(0, radarPlaybackFrameFromDisplayValue(displayValue = 3.9f, totalFrames = 5, isRtl = true))
        assertEquals(3, radarPlaybackFrameFromDisplayValue(displayValue = 3.4f, totalFrames = 5, isRtl = false))
        assertEquals(1, radarPlaybackFrameFromDisplayValue(displayValue = 3.4f, totalFrames = 5, isRtl = true))
        assertEquals(0, radarPlaybackFrameFromDisplayValue(displayValue = 9f, totalFrames = 0, isRtl = true))
    }
}
