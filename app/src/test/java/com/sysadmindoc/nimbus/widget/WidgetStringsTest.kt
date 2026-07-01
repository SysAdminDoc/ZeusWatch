package com.sysadmindoc.nimbus.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WidgetStringsTest {

    @Test
    fun `formats compact widget labels with caller supplied resources`() = withUsLocale {
        val strings = testWidgetStrings()

        assertEquals("Feels 72 / 41%", strings.feelsHumidity(feelsLike = 72, humidity = 41))
        assertEquals("H 81", strings.highTemp(81))
        assertEquals("L 57", strings.lowTemp(57))
        assertEquals("Denver weather: 72 degrees", strings.savedCityContentDescription("Denver", "72 degrees"))
    }

    @Test
    fun `updated content descriptions distinguish live and stale badges`() = withUsLocale {
        val strings = testWidgetStrings()

        assertEquals("Data updated just now. Tap to refresh now.", strings.updatedContentDescription("Live"))
        assertEquals("Data updated 17m ago. Tap to refresh now.", strings.updatedContentDescription("17m"))
    }

    @Test
    fun `weather descriptions use mapped text before generic fallback`() {
        val strings = testWidgetStrings()

        assertEquals("Clear sky", strings.weatherDescription(code = 0, isDay = true))
        assertEquals("Thunderstorm", strings.weatherDescription(code = 95, isDay = false))
        assertEquals("Weather icon", strings.weatherDescription(code = 1234, isDay = true))
    }

    private fun testWidgetStrings() = WidgetStrings(
        emptyTitle = "No weather",
        smallEmptyMessage = "Open ZeusWatch",
        mediumEmptyMessage = "Open ZeusWatch for weather",
        largeEmptyMessage = "Open ZeusWatch for a full forecast",
        stripEmptyMessage = "Hourly forecast unavailable",
        savedCitiesEmptyMessage = "Saved cities unavailable",
        currentEyebrow = "Current",
        overviewEyebrow = "Overview",
        detailedOutlookEyebrow = "Detailed outlook",
        savedCitiesEyebrow = "Saved cities",
        nextEyebrow = "Next",
        next3Days = "Next 3 days",
        next6Hours = "Next 6 hours",
        fiveDayOutlook = "5-day outlook",
        tapToOpen = "Tap to open",
        feelsHumidityFormat = "Feels %1\$d / %2\$d%%",
        highTempFormat = "H %1\$d",
        lowTempFormat = "L %1\$d",
        updatedLive = "Live",
        updatedMinutesFormat = "%1\$dm",
        updatedHoursFormat = "%1\$dh",
        updatedLiveContentDescription = "Data updated just now. Tap to refresh now.",
        updatedStaleContentDescriptionFormat = "Data updated %1\$s ago. Tap to refresh now.",
        weatherIconContentDescription = "Weather icon",
        weatherDescriptions = mapOf(
            0 to "Clear sky",
            95 to "Thunderstorm",
        ),
        savedCityUnavailableTemp = "--",
        savedCityContentDescriptionFormat = "%1\$s weather: %2\$s",
    )

    private fun withUsLocale(block: () -> Unit) {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}

class WidgetUtilsTest {

    @Test
    fun `weatherDescription returns model description for known codes`() {
        assertEquals("Clear", WidgetUtils.weatherDescription(code = 0, fallback = "Weather icon"))
        assertEquals("Thunderstorm", WidgetUtils.weatherDescription(code = 95, fallback = "Weather icon"))
    }

    @Test
    fun `weatherDescription returns fallback for unsupported codes`() {
        assertEquals("Weather icon", WidgetUtils.weatherDescription(code = 1234, fallback = "Weather icon"))
    }

    @Test
    fun `weatherDescription preserves explicit unknown code label`() {
        assertEquals("Unknown", WidgetUtils.weatherDescription(code = -1, fallback = "Weather icon"))
    }
}
