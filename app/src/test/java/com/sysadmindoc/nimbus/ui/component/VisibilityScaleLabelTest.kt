package com.sysadmindoc.nimbus.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibilityScaleLabelTest {
    @Test
    fun `well separated labels all keep their centered positions`() {
        val lefts = nonOverlappingScaleLabelLefts(
            centers = listOf(100f, 500f, 900f),
            labelWidths = listOf(50f, 50f, 50f),
            canvasWidth = 1000f,
            minGapPx = 4f,
        )
        assertEquals(listOf(75f, 475f, 875f), lefts)
    }

    @Test
    fun `clamped overlapping label on a tiny segment is skipped and later tier wins`() {
        // Mirrors the visibility bar: the 0-1 km segment center sits at 1% of
        // the width, so its label clamps to the left edge and collides with
        // the 1-4 km label.
        val lefts = nonOverlappingScaleLabelLefts(
            centers = listOf(10f, 50f, 140f, 300f, 600f, 900f),
            labelWidths = listOf(50f, 50f, 50f, 50f, 50f, 50f),
            canvasWidth = 1000f,
            minGapPx = 4f,
        )
        assertNull(lefts[0])
        assertEquals(25f, lefts[1])
        assertEquals(115f, lefts[2])
        assertEquals(275f, lefts[3])
        assertEquals(575f, lefts[4])
        assertEquals(875f, lefts[5])
    }

    @Test
    fun `overlap resolution is position based so mirrored rtl centers behave the same`() {
        // Same bar mirrored (descending centers, as drawn under RTL).
        val lefts = nonOverlappingScaleLabelLefts(
            centers = listOf(990f, 950f, 860f, 700f, 400f, 100f),
            labelWidths = listOf(50f, 50f, 50f, 50f, 50f, 50f),
            canvasWidth = 1000f,
            minGapPx = 4f,
        )
        assertNull(lefts[0])
        assertEquals(925f, lefts[1])
        assertEquals(835f, lefts[2])
        assertEquals(675f, lefts[3])
        assertEquals(375f, lefts[4])
        assertEquals(75f, lefts[5])
    }
}
