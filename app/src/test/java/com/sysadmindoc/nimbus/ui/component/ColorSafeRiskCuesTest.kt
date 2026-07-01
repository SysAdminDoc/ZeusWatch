package com.sysadmindoc.nimbus.ui.component

import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.PollenLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSafeRiskCuesTest {

    @Test
    fun `aqi cues expose one ordinal code per EPA tier`() {
        val cues = AqiLevel.entries.map(ColorSafeRiskCues::aqi)

        assertEquals(listOf(1, 2, 3, 4, 5, 6), cues.map { it.ordinal })
        assertEquals(6, cues.map { it.code }.distinct().size)
        assertEquals("AQI 1/6", cues.first().code)
        assertEquals("AQI 6/6", cues.last().code)
    }

    @Test
    fun `uv cues use stable non color risk buckets`() {
        val values = listOf(0.0, 3.0, 6.0, 8.0, 11.0)
        val cues = values.map(ColorSafeRiskCues::uv)

        assertEquals(listOf(1, 2, 3, 4, 5), cues.map { it.ordinal })
        assertEquals(listOf("UV 1/5", "UV 2/5", "UV 3/5", "UV 4/5", "UV 5/5"), cues.map { it.code })
    }

    @Test
    fun `pollen cues map none plus four visible levels`() {
        val cues = PollenLevel.entries.map(ColorSafeRiskCues::pollen)

        assertEquals(listOf(0, 1, 2, 3, 4), cues.map { it.ordinal })
        assertEquals("P0/4", cues.first().code)
        assertEquals("P4/4", cues.last().code)
    }

    @Test
    fun `precipitation cues bucket probability without relying on alpha`() {
        val cues = listOf(0, 1, 24, 25, 49, 50, 69, 70, 100).map(ColorSafeRiskCues::precipitation)

        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4), cues.map { it.ordinal })
        assertEquals(
            listOf("R0/4", "R1/4", "R1/4", "R2/4", "R2/4", "R3/4", "R3/4", "R4/4", "R4/4"),
            cues.map { it.code },
        )
    }

    @Test
    fun `chart boundary fractions stay ordered inside the visible track`() {
        val boundaries = ColorSafeRiskCues.aqiBoundaryFractions + ColorSafeRiskCues.uvBoundaryFractions

        assertTrue(boundaries.all { it > 0f && it < 1f })
        assertTrue(ColorSafeRiskCues.aqiBoundaryFractions.zipWithNext().all { (a, b) -> a < b })
        assertTrue(ColorSafeRiskCues.uvBoundaryFractions.zipWithNext().all { (a, b) -> a < b })
    }
}
