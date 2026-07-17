package com.sysadmindoc.nimbus.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class VicoTrendChartsTest {
    @Test
    fun `trendLabelIndices spaces four labels to match SpaceBetween positions`() {
        // Labels sit at 0%, 33%, 67%, 100% of the chart width, so the indices
        // must land at the same fractions of the series — not 0, 6, 12, 18.
        assertEquals(listOf(0, 8, 15, 23), trendLabelIndices(24))
        assertEquals(listOf(0, 8, 16, 24), trendLabelIndices(25))
    }

    @Test
    fun `trendLabelIndices covers first and last points exactly`() {
        val indices = trendLabelIndices(24)
        assertEquals(0, indices.first())
        assertEquals(23, indices.last())
    }

    @Test
    fun `trendLabelIndices deduplicates for short series`() {
        assertEquals(listOf(0, 1, 2, 3), trendLabelIndices(4))
        assertEquals(listOf(0, 1, 2), trendLabelIndices(3))
        assertEquals(listOf(0, 1), trendLabelIndices(2))
        assertEquals(listOf(0), trendLabelIndices(1))
    }

    @Test
    fun `trendLabelIndices handles empty series`() {
        assertEquals(emptyList<Int>(), trendLabelIndices(0))
    }
}
