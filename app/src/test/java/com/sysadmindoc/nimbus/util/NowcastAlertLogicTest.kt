package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NowcastAlertLogicTest {

    // Fix "now" at a known-aligned 15-min boundary so bucket math is obvious.
    private val now = LocalDateTime.of(2026, 4, 12, 14, 0, 0)

    private fun bucket(offsetMinutes: Int, mm: Double) =
        MinutelyPrecipitation(time = now.plusMinutes(offsetMinutes.toLong()), precipitation = mm)

    @Test
    fun `returns null when series has fewer than two buckets`() {
        assertNull(detectNowcastTransition(emptyList(), now))
        assertNull(detectNowcastTransition(listOf(bucket(0, 0.0)), now))
    }

    @Test
    fun `returns null when series is uniformly dry`() {
        val series = listOf(
            bucket(0, 0.0),
            bucket(15, 0.0),
            bucket(30, 0.05),
            bucket(45, 0.0),
        )
        assertNull(detectNowcastTransition(series, now))
    }

    @Test
    fun `returns null when series is uniformly wet`() {
        val series = listOf(
            bucket(0, 0.5),
            bucket(15, 0.7),
            bucket(30, 1.2),
            bucket(45, 0.3),
        )
        assertNull(detectNowcastTransition(series, now))
    }

    @Test
    fun `detects rain starting and captures peak intensity`() {
        val series = listOf(
            bucket(0, 0.0),
            bucket(15, 0.0),
            bucket(30, 0.5),
            bucket(45, 2.0), // peak within window
            bucket(60, 1.0),
        )
        val transition = detectNowcastTransition(series, now)
        assertTrue("expected RainStarting, got $transition", transition is NowcastTransition.RainStarting)
        transition as NowcastTransition.RainStarting
        assertEquals(30, transition.minutesUntil)
        assertEquals(2.0, transition.peakMm, 0.001)
    }

    @Test
    fun `detects rain stopping`() {
        val series = listOf(
            bucket(0, 1.5),
            bucket(15, 0.8),
            bucket(30, 0.0), // dry transition
            bucket(45, 0.0),
        )
        val transition = detectNowcastTransition(series, now)
        assertTrue("expected RainStopping, got $transition", transition is NowcastTransition.RainStopping)
        transition as NowcastTransition.RainStopping
        assertEquals(30, transition.minutesUntil)
    }

    @Test
    fun `ignores transitions beyond the look-ahead window`() {
        // Rain starts 90 min out — outside the default 60 min look-ahead
        val series = listOf(
            bucket(0, 0.0),
            bucket(30, 0.0),
            bucket(60, 0.0),
            bucket(90, 2.0),
        )
        assertNull(detectNowcastTransition(series, now))
    }

    @Test
    fun `only returns the first transition even if more exist downstream`() {
        val series = listOf(
            bucket(0, 0.0),   // dry
            bucket(15, 0.5),  // start
            bucket(30, 0.0),  // stop — ignored by this call
            bucket(45, 0.5),
        )
        val transition = detectNowcastTransition(series, now)
        assertTrue(transition is NowcastTransition.RainStarting)
    }

    @Test
    fun `signature is stable for the same bucket timestamp`() {
        val t1 = NowcastTransition.RainStarting(now.plusMinutes(15), 15, 0.5)
        val t2 = NowcastTransition.RainStarting(now.plusMinutes(15), 15, 2.0) // different peak
        // Signature keys on time only, not intensity, so the dedupe store
        // doesn't spam a user when a forecast sharpens its peak estimate.
        assertEquals(transitionSignature(t1), transitionSignature(t2))
    }
}
