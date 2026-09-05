package com.sysadmindoc.nimbus.ui.component

import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #55: a country-wide feed answers one query with one alert per district,
 * and the banner drew all of them in one column.
 */
class AlertBannerGroupingTest {

    private fun alert(
        id: String,
        event: String,
        severity: AlertSeverity = AlertSeverity.SEVERE,
        area: String = "",
    ) = WeatherAlert(
        id = id,
        event = event,
        headline = "$event headline",
        description = "",
        instruction = null,
        severity = severity,
        urgency = AlertUrgency.EXPECTED,
        certainty = "Likely",
        senderName = "Test",
        areaDescription = area,
        effective = null,
        expires = null,
        response = null,
    )

    @Test
    fun `alerts sharing an event and severity collapse into one row`() {
        val alerts = listOf(
            alert("1", "Heavy Rain", area = "Gorakhpur"),
            alert("2", "Heavy Rain", area = "Sundargarh district of Odisha"),
            alert("3", "Heavy Rain", area = "Dhalai, North Tripura"),
        )

        val groups = groupAlertsForBanner(alerts)

        assertEquals(1, groups.size)
        assertEquals(3, groups[0].collapsedCount)
        assertEquals("1", groups[0].primary.id)
        assertEquals(
            listOf("Gorakhpur", "Sundargarh district of Odisha", "Dhalai, North Tripura"),
            groups[0].areas,
        )
    }

    @Test
    fun `the same event at a different severity stays a separate row`() {
        val groups = groupAlertsForBanner(
            listOf(
                alert("1", "Heavy Rain", severity = AlertSeverity.EXTREME),
                alert("2", "Heavy Rain", severity = AlertSeverity.SEVERE),
            ),
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun `event names that differ only by case or padding are one row`() {
        val groups = groupAlertsForBanner(
            listOf(
                alert("1", "Heavy Rain"),
                alert("2", "  heavy rain "),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].collapsedCount)
    }

    @Test
    fun `blank and duplicate areas are dropped from the collapsed row`() {
        val groups = groupAlertsForBanner(
            listOf(
                alert("1", "Heavy Rain", area = "Jaipur"),
                alert("2", "Heavy Rain", area = "   "),
                alert("3", "Heavy Rain", area = "Jaipur"),
            ),
        )

        assertEquals(listOf("Jaipur"), groups[0].areas)
        assertEquals(3, groups[0].collapsedCount)
    }

    @Test
    fun `a national feed collapses to a handful of rows and the rest are held back`() {
        // 54 alerts across 8 distinct event-plus-severity pairs, the shape the
        // WMO SWIC feed returned for India when the issue was filed.
        val events = listOf(
            "Heavy Rain" to AlertSeverity.EXTREME,
            "Heavy Rain" to AlertSeverity.SEVERE,
            "Moderate Rain" to AlertSeverity.SEVERE,
            "Thunderstorm with Lightning" to AlertSeverity.SEVERE,
            "Thunder shower" to AlertSeverity.MODERATE,
            "Extremely heavy" to AlertSeverity.SEVERE,
            "Squall" to AlertSeverity.MODERATE,
            "Hailstorm" to AlertSeverity.MINOR,
        )
        val alerts = (0 until 54).map { index ->
            val (event, severity) = events[index % events.size]
            alert("alert-$index", event, severity, area = "district-$index")
        }

        val groups = groupAlertsForBanner(alerts)
        val visible = groups.take(MAX_COLLAPSED_ALERT_GROUPS)
        val hidden = groups.size - visible.size

        assertEquals(8, groups.size)
        assertEquals(5, visible.size)
        assertEquals(3, hidden)
        assertEquals(54, groups.sumOf { it.collapsedCount })
    }

    @Test
    fun `a short list is shown whole`() {
        val groups = groupAlertsForBanner(
            listOf(
                alert("1", "Tornado Warning", severity = AlertSeverity.EXTREME),
                alert("2", "Flood Watch", severity = AlertSeverity.MODERATE),
            ),
        )

        assertTrue(groups.size <= MAX_COLLAPSED_ALERT_GROUPS)
        assertEquals(2, groups.size)
    }
}
