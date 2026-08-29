package com.sysadmindoc.nimbus.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These come out of a settings file the user can edit and re-import, so the
 * interesting cases are the nonsensical ones.
 */
class ActivityThresholdsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `inside the comfortable band scores full marks`() {
        val thresholds = ActivityThresholds()

        assertEquals(100, thresholds.temperatureScore(20.0))
        assertEquals(100, thresholds.temperatureScore(10.0))
        assertEquals(100, thresholds.temperatureScore(28.0))
    }

    @Test
    fun `outside the band the score falls off rather than dropping to zero`() {
        // One degree over is not the same as ten, and a cliff at the edge makes
        // the threshold feel broken to anyone who sets it near where they live.
        val thresholds = ActivityThresholds()
        val justOver = thresholds.temperatureScore(29.0)
        val wellOver = thresholds.temperatureScore(40.0)

        assertTrue(justOver in 1..99)
        assertTrue(wellOver < justOver)
        assertTrue(wellOver >= 0)
    }

    @Test
    fun `raising a threshold raises the score at that value`() {
        val strict = ActivityThresholds(maxWindKmh = 15.0)
        val relaxed = ActivityThresholds(maxWindKmh = 40.0)

        assertTrue(relaxed.windScore(35.0) > strict.windScore(35.0))
        assertEquals(100, relaxed.windScore(35.0))
    }

    @Test
    fun `defaults are recognisable as defaults`() {
        assertTrue(ActivityThresholds().isDefault)
        assertFalse(ActivityThresholds(maxAqi = 90).isDefault)
    }

    @Test
    fun `a min above a max is swapped rather than producing an empty band`() {
        // Nothing would ever score 100 against an inverted band, so every
        // activity would report no window with no explanation.
        val inverted = ActivityThresholds(minComfortableTempC = 25.0, maxComfortableTempC = 5.0)

        val fixed = ActivityThresholds.sanitize(inverted)

        assertTrue(fixed.minComfortableTempC <= fixed.maxComfortableTempC)
        assertEquals(100, fixed.temperatureScore(15.0))
    }

    @Test
    fun `values outside the offered ranges are clamped back in`() {
        val absurd = ActivityThresholds(
            maxPrecipitationChance = 5_000,
            maxWindKmh = -40.0,
            maxUvIndex = 99.0,
            maxAqi = -5,
        )

        val fixed = ActivityThresholds.sanitize(absurd)

        assertTrue(fixed.maxPrecipitationChance in ActivityThresholds.PRECIP_RANGE)
        assertTrue(fixed.maxWindKmh in ActivityThresholds.WIND_RANGE)
        assertTrue(fixed.maxUvIndex in ActivityThresholds.UV_RANGE)
        assertTrue(fixed.maxAqi in ActivityThresholds.AQI_RANGE)
    }

    @Test
    fun `sanitizing something already sane changes nothing`() {
        val thresholds = ActivityThresholds(maxWindKmh = 25.0, maxAqi = 90)

        assertEquals(thresholds, ActivityThresholds.sanitize(thresholds))
    }

    @Test
    fun `thresholds survive a round trip through JSON`() {
        // This is how they reach an exported settings file and come back.
        val thresholds = ActivityThresholds(
            minComfortableTempC = 4.0,
            maxComfortableTempC = 33.0,
            maxPrecipitationChance = 45,
            maxWindKmh = 32.0,
            maxUvIndex = 8.0,
            maxAqi = 120,
        )

        val restored = json.decodeFromString<ActivityThresholds>(json.encodeToString(thresholds))

        assertEquals(thresholds, restored)
    }

    @Test
    fun `a settings file written before this feature decodes to the defaults`() {
        val restored = json.decodeFromString<ActivityThresholds>("{}")

        assertEquals(ActivityThresholds(), restored)
        assertTrue(restored.isDefault)
    }
}
