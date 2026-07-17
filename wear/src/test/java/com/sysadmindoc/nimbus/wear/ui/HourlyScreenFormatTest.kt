package com.sysadmindoc.nimbus.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Hour labels must honor the system 12/24-hour preference and the device
 * locale instead of hardcoding English "AM"/"PM".
 */
class HourlyScreenFormatTest {

    @Test
    fun `12-hour format renders localized am pm markers`() {
        val formatter = hourLabelFormatter(is24Hour = false, locale = Locale.US)
        assertEquals("12AM", formatHour("2026-05-17T00:00", formatter))
        assertEquals("9AM", formatHour("2026-05-17T09:00", formatter))
        assertEquals("12PM", formatHour("2026-05-17T12:00", formatter))
        assertEquals("3PM", formatHour("2026-05-17T15:00", formatter))
        assertEquals("11PM", formatHour("2026-05-17T23:00", formatter))
    }

    @Test
    fun `24-hour format renders hour and minutes`() {
        val formatter = hourLabelFormatter(is24Hour = true, locale = Locale.GERMANY)
        assertEquals("00:00", formatHour("2026-05-17T00:00", formatter))
        assertEquals("09:00", formatHour("2026-05-17T09:00", formatter))
        assertEquals("15:00", formatHour("2026-05-17T15:00", formatter))
        assertEquals("23:30", formatHour("2026-05-17T23:30", formatter))
    }

    @Test
    fun `non-English locales use their own day-period text`() {
        val formatter = hourLabelFormatter(is24Hour = false, locale = Locale.forLanguageTag("es"))
        val label = formatHour("2026-05-17T15:00", formatter)
        // Spanish CLDR renders p.m. variants, never the bare English "PM".
        assertEquals(false, label.endsWith("PM"))
    }

    @Test
    fun `malformed input degrades to the raw hour text`() {
        val formatter = hourLabelFormatter(is24Hour = false, locale = Locale.US)
        assertEquals("10", formatHour("2026-05-17T10:xx", formatter))
        assertEquals("garbage", formatHour("garbage", formatter))
    }
}
