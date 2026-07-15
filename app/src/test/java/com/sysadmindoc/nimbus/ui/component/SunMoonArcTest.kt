package com.sysadmindoc.nimbus.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SunMoonArcTest {

    @Test
    fun `moonArcState survives same-date labels for an overnight transit`() {
        // Producers label moonset with the SAME calendar date as moonrise, so
        // for ~half the lunar month the moon "sets" (early morning) before it
        // rises (evening) that date. The arc must roll the set forward a day
        // instead of vanishing on a non-positive window.
        val moonrise = LocalDateTime.of(2026, 4, 15, 20, 0)
        val moonset = LocalDateTime.of(2026, 4, 15, 6, 0) // actually 06:00 next day

        val atMidnight = moonArcState(moonrise, moonset, LocalDateTime.of(2026, 4, 16, 1, 0))

        assertTrue(atMidnight != null)
        assertTrue(atMidnight!!.isUp)
        // 20:00 → 06:00(+1d) is 10h; 01:00 is 5h in → halfway across the arc.
        assertEquals(0.5f, atMidnight.progress, 0.01f)
    }

    @Test
    fun `moonArcState overnight transit is down outside the rise-set window`() {
        val moonrise = LocalDateTime.of(2026, 4, 15, 20, 0)
        val moonset = LocalDateTime.of(2026, 4, 15, 6, 0)

        val midday = moonArcState(moonrise, moonset, LocalDateTime.of(2026, 4, 15, 12, 0))

        assertTrue(midday != null)
        assertFalse(midday!!.isUp)
        assertEquals(0f, midday.progress, 0f)
    }

    @Test
    fun `moonArcState same-day window keeps its original math`() {
        val moonrise = LocalDateTime.of(2026, 4, 15, 6, 0)
        val moonset = LocalDateTime.of(2026, 4, 15, 18, 0)

        val state = moonArcState(moonrise, moonset, LocalDateTime.of(2026, 4, 15, 9, 0))

        assertTrue(state != null)
        assertTrue(state!!.isUp)
        assertEquals(0.25f, state.progress, 0.01f)
    }

    @Test
    fun `moonArcState returns null when either endpoint is missing`() {
        val time = LocalDateTime.of(2026, 4, 15, 6, 0)
        assertNull(moonArcState(null, time, time))
        assertNull(moonArcState(time, null, time))
    }
}
