package com.sysadmindoc.nimbus.widget

import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetRefreshWorkerLogicTest {

    @Test
    fun `buildWidgetRefreshPlan groups duplicate coordinates but preserves widget labels`() {
        val savedLocations = listOf(
            SavedLocationEntity(
                id = 1L,
                name = "My Location",
                latitude = 39.73921,
                longitude = -104.99031,
                isCurrentLocation = true,
                sortOrder = -1,
            ),
            SavedLocationEntity(
                id = 2L,
                name = "Denver",
                latitude = 39.73924,
                longitude = -104.99034,
                sortOrder = 0,
            ),
            SavedLocationEntity(
                id = 3L,
                name = "Boulder",
                latitude = 40.01499,
                longitude = -105.27050,
                sortOrder = 1,
            ),
        )

        val plan = buildWidgetRefreshPlan(
            widgetMappings = linkedMapOf(
                101 to 1L,
                102 to 2L,
                103 to 3L,
            ),
            savedLocations = savedLocations,
        )

        assertEquals(emptyList<Int>(), plan.orphanedWidgetIds)
        assertEquals(2, plan.requests.size)

        val denverRequest = plan.requests.first { it.assignments.size == 2 }
        assertEquals(widgetLocationKey(39.73921, -104.99031), denverRequest.key)
        assertEquals(
            listOf(
                WidgetRefreshAssignment(appWidgetId = 101, displayName = "My Location"),
                WidgetRefreshAssignment(appWidgetId = 102, displayName = "Denver"),
            ),
            denverRequest.assignments,
        )

        val boulderRequest = plan.requests.first { it.assignments.size == 1 }
        assertEquals(widgetLocationKey(40.01499, -105.27050), boulderRequest.key)
        assertEquals(
            listOf(WidgetRefreshAssignment(appWidgetId = 103, displayName = "Boulder")),
            boulderRequest.assignments,
        )
    }

    @Test
    fun `buildWidgetRefreshPlan reports orphaned widget mappings`() {
        val plan = buildWidgetRefreshPlan(
            widgetMappings = linkedMapOf(
                101 to 1L,
                102 to 99L,
            ),
            savedLocations = listOf(
                SavedLocationEntity(
                    id = 1L,
                    name = "Denver",
                    latitude = 39.7392,
                    longitude = -104.9903,
                    sortOrder = 0,
                ),
            ),
        )

        assertEquals(listOf(102), plan.orphanedWidgetIds)
        assertEquals(1, plan.requests.size)
        assertEquals(
            listOf(WidgetRefreshAssignment(appWidgetId = 101, displayName = "Denver")),
            plan.requests.single().assignments,
        )
    }

    @Test
    fun `buildWidgetHourlyItems keeps first twelve forecast entries`() {
        val hourly = (0 until 14).map { hourOffset ->
            HourlyConditions(
                time = LocalDateTime.of(2026, 4, 15, 6, 0).plusHours(hourOffset.toLong()),
                temperature = 10.0 + hourOffset,
                feelsLike = null,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = true,
                precipitationProbability = hourOffset,
                precipitation = null,
                windSpeed = null,
                windDirection = null,
                humidity = null,
                uvIndex = null,
                cloudCover = null,
                visibility = null,
            )
        }

        val items = buildWidgetHourlyItems(hourly, hourly.first().time) { it }

        assertEquals(12, items.size)
        assertEquals("Now", items.first().hour)
        assertEquals(10, items.first().temp)
        assertEquals(21, items.last().temp)
    }

    @Test
    fun `buildWidgetDailyItems labels days from forecast anchor date`() {
        val today = LocalDate.of(2026, 4, 15)
        val items = buildWidgetDailyItems(
            daily = listOf(
                DailyConditions(
                    date = today,
                    weatherCode = WeatherCode.CLEAR_SKY,
                    temperatureHigh = 20.0,
                    temperatureLow = 10.0,
                    precipitationProbability = 10,
                    precipitationSum = null,
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = null,
                    windSpeedMax = null,
                    windDirectionDominant = null,
                ),
                DailyConditions(
                    date = today.plusDays(1),
                    weatherCode = WeatherCode.OVERCAST,
                    temperatureHigh = 18.0,
                    temperatureLow = 9.0,
                    precipitationProbability = 40,
                    precipitationSum = null,
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = null,
                    windSpeedMax = null,
                    windDirectionDominant = null,
                ),
                DailyConditions(
                    date = today.plusDays(2),
                    weatherCode = WeatherCode.RAIN_MODERATE,
                    temperatureHigh = 16.0,
                    temperatureLow = 8.0,
                    precipitationProbability = 60,
                    precipitationSum = 5.0,
                    sunrise = null,
                    sunset = null,
                    uvIndexMax = null,
                    windSpeedMax = null,
                    windDirectionDominant = null,
                ),
            ),
            today = today,
        ) { it }

        assertEquals("Today", items[0].day)
        assertEquals("Tmrw", items[1].day)
        assertTrue(items[2].day.isNotBlank())
        assertEquals(16, items[2].high)
    }
}
