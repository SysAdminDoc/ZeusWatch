package com.sysadmindoc.nimbus.widget

import com.sysadmindoc.nimbus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure-function helpers in [WidgetTheme] that drive
 * every Glance home-screen widget. These are the layer the user actually
 * sees on a glanceable surface, so a regression here turns "live" into
 * "live since the Bronze Age" or paints rain icons over a clear sky.
 *
 * The composables themselves require Glance's testing harness (1.2.0+),
 * which the repo isn't on yet — but the freshness pill logic and the
 * WMO icon resolver are pure functions and fully testable today.
 */
class WidgetThemeTest {

    // ── widgetUpdatedLabel ──────────────────────────────────────────────

    @Test
    fun `widgetUpdatedLabel returns null when no timestamp has been recorded`() {
        assertNull(widgetUpdatedLabel(updatedAt = 0L))
        assertNull(widgetUpdatedLabel(updatedAt = -1L))
    }

    @Test
    fun `widgetUpdatedLabel returns Live in the first five minutes`() {
        val now = System.currentTimeMillis()
        // Within the window — including the boundary just under 5 min.
        assertEquals("Live", widgetUpdatedLabel(updatedAt = now))
        assertEquals("Live", widgetUpdatedLabel(updatedAt = now - 30_000L))
        assertEquals("Live", widgetUpdatedLabel(updatedAt = now - (4 * 60_000L + 59_000L)))
    }

    @Test
    fun `widgetUpdatedLabel switches to minute format at five minute boundary`() {
        val now = System.currentTimeMillis()
        // 5 minutes exactly switches over.
        assertEquals("5m", widgetUpdatedLabel(updatedAt = now - 5 * 60_000L))
        assertEquals("17m", widgetUpdatedLabel(updatedAt = now - 17 * 60_000L))
        assertEquals("59m", widgetUpdatedLabel(updatedAt = now - 59 * 60_000L))
    }

    @Test
    fun `widgetUpdatedLabel switches to hour format at sixty minute boundary`() {
        val now = System.currentTimeMillis()
        assertEquals("1h", widgetUpdatedLabel(updatedAt = now - 60 * 60_000L))
        assertEquals("3h", widgetUpdatedLabel(updatedAt = now - 3 * 60 * 60_000L))
        assertEquals("24h", widgetUpdatedLabel(updatedAt = now - 24 * 60 * 60_000L))
    }

    @Test
    fun `widgetUpdatedLabel clamps negative elapsed time from NTP rollback to Live`() {
        // A clock that went backwards (NTP correction, manual change) must
        // not produce "-3m" — that's worse than no badge. The function uses
        // coerceAtLeast(0) which lands us in the <5 min "Live" branch.
        val now = System.currentTimeMillis()
        assertEquals("Live", widgetUpdatedLabel(updatedAt = now + 60_000L))
        assertEquals("Live", widgetUpdatedLabel(updatedAt = now + 24L * 60 * 60_000L))
    }

    @Test
    fun `widgetUpdatedLabel honors caller-supplied format overrides`() {
        val now = System.currentTimeMillis()
        // Localized callers can pass French / German / Spanish format strings.
        assertEquals(
            "vor 7 Min.",
            widgetUpdatedLabel(
                updatedAt = now - 7 * 60_000L,
                liveLabel = "Jetzt",
                minuteFormat = "vor %1\$d Min.",
                hourFormat = "vor %1\$d Std.",
            ),
        )
        assertEquals(
            "Jetzt",
            widgetUpdatedLabel(
                updatedAt = now,
                liveLabel = "Jetzt",
                minuteFormat = "vor %1\$d Min.",
                hourFormat = "vor %1\$d Std.",
            ),
        )
        assertEquals(
            "vor 2 Std.",
            widgetUpdatedLabel(
                updatedAt = now - 2 * 60 * 60_000L,
                liveLabel = "Jetzt",
                minuteFormat = "vor %1\$d Min.",
                hourFormat = "vor %1\$d Std.",
            ),
        )
    }

    // ── weatherIconRes ──────────────────────────────────────────────────

    @Test
    fun `weatherIconRes maps clear sky codes with day-night variant`() {
        assertEquals(R.drawable.ic_w_sunny, weatherIconRes(code = 0, isDay = true))
        assertEquals(R.drawable.ic_w_sunny, weatherIconRes(code = 1, isDay = true))
        assertEquals(R.drawable.ic_w_night, weatherIconRes(code = 0, isDay = false))
        assertEquals(R.drawable.ic_w_night, weatherIconRes(code = 1, isDay = false))
    }

    @Test
    fun `weatherIconRes maps partly cloudy with day-night variant and overcast to cloudy`() {
        // Mirrors WeatherNotificationHelper: partly cloudy gets its dedicated
        // glyph by day and falls back to cloudy at night (no moon variant).
        assertEquals(R.drawable.ic_w_partly_cloudy, weatherIconRes(code = 2, isDay = true))
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = 2, isDay = false))
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = 3, isDay = true))
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = 3, isDay = false))
    }

    @Test
    fun `weatherIconRes maps fog codes to the fog glyph`() {
        assertEquals(R.drawable.ic_w_fog, weatherIconRes(code = 45, isDay = true))
        assertEquals(R.drawable.ic_w_fog, weatherIconRes(code = 48, isDay = false))
    }

    @Test
    fun `weatherIconRes maps drizzle rain and freezing rain to the rain glyph`() {
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 51, isDay = true))
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 55, isDay = true))
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 57, isDay = false))
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 61, isDay = true))
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 65, isDay = true))
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 67, isDay = false))
    }

    @Test
    fun `weatherIconRes maps snow and snow grains and snow showers to the snow glyph`() {
        assertEquals(R.drawable.ic_w_snow, weatherIconRes(code = 71, isDay = true))
        assertEquals(R.drawable.ic_w_snow, weatherIconRes(code = 75, isDay = false))
        assertEquals(R.drawable.ic_w_snow, weatherIconRes(code = 77, isDay = true))
        assertEquals(R.drawable.ic_w_snow, weatherIconRes(code = 85, isDay = true))
        assertEquals(R.drawable.ic_w_snow, weatherIconRes(code = 86, isDay = false))
    }

    @Test
    fun `weatherIconRes maps showers to rain and thunderstorms to the thunderstorm glyph`() {
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 80, isDay = true))
        assertEquals(R.drawable.ic_w_rain, weatherIconRes(code = 82, isDay = false))
        assertEquals(R.drawable.ic_w_thunderstorm, weatherIconRes(code = 95, isDay = true))
        assertEquals(R.drawable.ic_w_thunderstorm, weatherIconRes(code = 96, isDay = true))
        assertEquals(R.drawable.ic_w_thunderstorm, weatherIconRes(code = 99, isDay = false))
    }

    @Test
    fun `weatherIconRes falls back to cloudy for unsupported codes`() {
        // 4 and 50 sit in the small gaps in the WMO range — defensive default
        // keeps a glyph on screen so the widget never goes blank.
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = 4, isDay = true))
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = 50, isDay = true))
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = 100, isDay = false))
        assertEquals(R.drawable.ic_w_cloudy, weatherIconRes(code = -1, isDay = true))
    }
}
