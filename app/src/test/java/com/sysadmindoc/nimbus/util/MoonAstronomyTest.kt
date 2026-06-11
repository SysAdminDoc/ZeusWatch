package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MoonAstronomyTest {

    // ── illuminationPercent — Meeus 48.3 phase-angle sign regression ─────────
    //
    // A sign error in the atan2 x-component, (R cos psi − Δ) instead of
    // (Δ − R cos psi), inverts the illumination curve: ~100% at new moon
    // and ~0% at full moon. Pin both extremes against known lunations.

    @Test
    fun `illumination is near zero at the 2024-01-11 new moon`() {
        // New moon: 2024-01-11 11:57 UTC
        val at = ZonedDateTime.of(2024, 1, 11, 12, 0, 0, 0, ZoneId.of("UTC"))
        val pct = MoonAstronomy.illuminationPercent(at)
        assertTrue("New-moon illumination should be <5%, was $pct", pct < 5.0)
    }

    @Test
    fun `illumination is near full at the 2024-01-25 full moon`() {
        // Full moon: 2024-01-25 17:54 UTC
        val at = ZonedDateTime.of(2024, 1, 25, 18, 0, 0, 0, ZoneId.of("UTC"))
        val pct = MoonAstronomy.illuminationPercent(at)
        assertTrue("Full-moon illumination should be >95%, was $pct", pct > 95.0)
    }

    @Test
    fun `illumination is roughly half at the 2024-01-18 first quarter`() {
        // First quarter: 2024-01-18 03:53 UTC — sign inversion would not be
        // caught here, but a broken distance/elongation term would be.
        val at = ZonedDateTime.of(2024, 1, 18, 4, 0, 0, 0, ZoneId.of("UTC"))
        val pct = MoonAstronomy.illuminationPercent(at)
        assertTrue("First-quarter illumination should be ~50%, was $pct", pct in 40.0..60.0)
    }

    // ── riseSetForDate — DST wall-clock labeling ──────────────────────────────

    @Test
    fun `rise and set labels never land in the skipped DST hour`() {
        // 2024-03-10 America/New_York springs forward: 02:00–02:59 wall time
        // does not exist. Labeling a crossing by the loop-hour index could
        // fabricate such a time; labeling from the sampled ZonedDateTime
        // cannot, because the sampled instants skip the gap.
        val zone = ZoneId.of("America/New_York")
        val times = MoonAstronomy.riseSetForDate(LocalDate.of(2024, 3, 10), 40.71, -74.01, zone)
        for (t in listOfNotNull(times.rise, times.set)) {
            assertTrue(
                "Label $t falls inside the nonexistent 02:00–02:59 DST gap",
                t < LocalTime.of(2, 0) || t >= LocalTime.of(3, 0),
            )
        }
    }
}
