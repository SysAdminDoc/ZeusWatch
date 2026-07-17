package com.sysadmindoc.nimbus.wear.ui

import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * "Today"/"Tmrw" labeling must anchor on the forecast location's calendar
 * (the first synced daily entry), not the watch clock — across a date
 * boundary the watch's LocalDate.now() labels tomorrow's row "Today".
 */
class DailyScreenFormatTest {

    @Test
    fun `dailyAnchorDate uses the first parseable entry date`() {
        val daily = listOf(
            WearDailyEntry(date = "2026-04-14", weatherCode = 0, high = 20, low = 10),
            WearDailyEntry(date = "2026-04-15", weatherCode = 0, high = 21, low = 11),
        )
        assertEquals(LocalDate.of(2026, 4, 14), dailyAnchorDate(daily))
    }

    @Test
    fun `dailyAnchorDate skips malformed leading entries`() {
        val daily = listOf(
            WearDailyEntry(date = "not-a-date", weatherCode = 0, high = 20, low = 10),
            WearDailyEntry(date = "2026-04-15", weatherCode = 0, high = 21, low = 11),
        )
        assertEquals(LocalDate.of(2026, 4, 15), dailyAnchorDate(daily))
    }

    @Test
    fun `dailyAnchorDate is null when nothing parses`() {
        assertNull(dailyAnchorDate(emptyList()))
        assertNull(dailyAnchorDate(listOf(WearDailyEntry(date = "Mon", weatherCode = 0, high = 1, low = 0))))
    }

    @Test
    fun `formatDay labels the anchor date today even when the watch date differs`() {
        // Simulates the phone location being a day ahead of the watch: the
        // anchor (location "today") drives the label, not LocalDate.now().
        val anchor = LocalDate.of(2026, 4, 15)
        assertEquals("Today", formatDay("2026-04-15", "Today", "Tmrw", anchor))
        assertEquals("Tmrw", formatDay("2026-04-16", "Today", "Tmrw", anchor))
    }

    @Test
    fun `formatDay falls back to short weekday names beyond tomorrow`() {
        val anchor = LocalDate.of(2026, 4, 13)
        // 2026-04-17 is a Friday.
        val label = formatDay("2026-04-17", "Today", "Tmrw", anchor)
        assertEquals(
            LocalDate.of(2026, 4, 17).dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            ),
            label,
        )
    }

    @Test
    fun `formatDay degrades to raw date suffix on malformed input`() {
        val anchor = LocalDate.of(2026, 4, 13)
        assertEquals("04-99", formatDay("2026-04-99", "Today", "Tmrw", anchor))
        assertEquals("weird", formatDay("weird", "Today", "Tmrw", anchor))
    }
}
