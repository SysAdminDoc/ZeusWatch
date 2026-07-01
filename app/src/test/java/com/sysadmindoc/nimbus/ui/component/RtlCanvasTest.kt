package com.sysadmindoc.nimbus.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class RtlCanvasTest {
    @Test
    fun `rtlCanvasX mirrors points across the canvas width`() {
        assertEquals(25f, rtlCanvasX(25f, 100f, isRtl = false))
        assertEquals(75f, rtlCanvasX(25f, 100f, isRtl = true))
        assertEquals(0f, rtlCanvasX(100f, 100f, isRtl = true))
    }

    @Test
    fun `rtlCanvasRectLeft mirrors rectangle bounds without changing width`() {
        assertEquals(10f, rtlCanvasRectLeft(left = 10f, rectWidth = 20f, canvasWidth = 100f, isRtl = false))
        assertEquals(70f, rtlCanvasRectLeft(left = 10f, rectWidth = 20f, canvasWidth = 100f, isRtl = true))
    }

    @Test
    fun `centeredCanvasLabelLeft clamps labels inside the canvas`() {
        assertEquals(0f, centeredCanvasLabelLeft(centerX = 4f, labelWidth = 20f, canvasWidth = 100f))
        assertEquals(40f, centeredCanvasLabelLeft(centerX = 50f, labelWidth = 20f, canvasWidth = 100f))
        assertEquals(80f, centeredCanvasLabelLeft(centerX = 98f, labelWidth = 20f, canvasWidth = 100f))
    }

    @Test
    fun `logicalCanvasX converts display x back to chronological x in rtl`() {
        assertEquals(20f, logicalCanvasX(displayX = 20f, width = 100f, isRtl = false))
        assertEquals(80f, logicalCanvasX(displayX = 20f, width = 100f, isRtl = true))
    }
}
