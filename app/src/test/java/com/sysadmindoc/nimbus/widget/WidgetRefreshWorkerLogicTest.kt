package com.sysadmindoc.nimbus.widget

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class WidgetRefreshWorkerLogicTest {

    @Test
    fun `background refresh cadence uses Android periodic work minimum`() {
        assertEquals(15L, WIDGET_BACKGROUND_REFRESH_INTERVAL_MINUTES)
    }

    @Test
    fun `battery skip triggers only when critically low and not charging`() {
        assertTrue(shouldSkipWidgetRefreshForBattery(batteryLevel = 10, isCharging = false))
        assertTrue(shouldSkipWidgetRefreshForBattery(batteryLevel = 15, isCharging = false))
        assertFalse(shouldSkipWidgetRefreshForBattery(batteryLevel = 16, isCharging = false))
        assertFalse(shouldSkipWidgetRefreshForBattery(batteryLevel = 100, isCharging = false))
    }

    @Test
    fun `battery skip never triggers while charging`() {
        assertFalse(shouldSkipWidgetRefreshForBattery(batteryLevel = 5, isCharging = true))
        assertFalse(shouldSkipWidgetRefreshForBattery(batteryLevel = 0, isCharging = true))
    }

    @Test
    fun `battery skip proceeds when capacity is unknown`() {
        // Emulators / missing fuel gauge report null — never skip forever.
        assertFalse(shouldSkipWidgetRefreshForBattery(batteryLevel = null, isCharging = false))
        // Negative readings are also "unknown-ish" hardware glitches.
        assertFalse(shouldSkipWidgetRefreshForBattery(batteryLevel = -1, isCharging = false))
    }

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
                forecastSource = WeatherSourceProvider.OPEN_METEO_BOM.name,
                alertSource = WeatherSourceProvider.METEOALARM.name,
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
        assertEquals(WeatherSourceProvider.OPEN_METEO_BOM, boulderRequest.sourceOverrides.forecast)
        assertEquals(WeatherSourceProvider.METEOALARM, boulderRequest.sourceOverrides.alerts)
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
    fun `widgetLocationKey is locale safe and rounds to four decimals`() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("39.7392:-104.9903", widgetLocationKey(39.73924, -104.99034))
            assertEquals("-33.8688:151.2093", widgetLocationKey(-33.86882, 151.20930))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `savedCityLocationsForWidget excludes current location and caps sorted cities`() {
        val locations = listOf(
            savedLocation(id = 1L, name = "My Location", sortOrder = -1, isCurrentLocation = true),
            savedLocation(id = 2L, name = "Seattle", sortOrder = 3),
            savedLocation(id = 3L, name = "Austin", sortOrder = 1),
            savedLocation(id = 4L, name = "Boston", sortOrder = 1),
            savedLocation(id = 5L, name = "Denver", sortOrder = 4),
            savedLocation(id = 6L, name = "Chicago", sortOrder = 5),
            savedLocation(id = 7L, name = "Miami", sortOrder = 6),
        )

        val cities = savedCityLocationsForWidget(locations)

        assertEquals(listOf("Austin", "Boston", "Seattle", "Denver", "Chicago"), cities.map { it.name })
        assertFalse(cities.any { it.isCurrentLocation })
    }

    @Test
    fun `buildWidgetSavedCity converts weather into compact saved city row`() {
        val location = savedLocation(id = 42L, name = "Denver", sortOrder = 0)
        val weather = weatherDataFor(location)

        val city = buildWidgetSavedCity(location, weather) { celsius -> celsius * 9.0 / 5.0 + 32.0 }

        assertEquals(42L, city.locationId)
        assertEquals("Denver", city.locationName)
        assertEquals(68, city.temperature)
        assertEquals(77, city.high)
        assertEquals(50, city.low)
        assertEquals(WeatherCode.CLEAR_SKY.code, city.weatherCode)
        assertTrue(city.isDay)
        assertTrue(city.updatedAt > 0L)
        assertTrue(city.observedAt > 0L)
        assertEquals("Open-Meteo", city.sourceProvider)
    }

    @Test
    fun `buildWidgetWeatherData preserves app source observation time and cache age`() {
        val location = savedLocation(id = 24L, name = "Boulder", sortOrder = 0)
        val observationTime = LocalDateTime.of(2026, 4, 15, 12, 0)
        val lastUpdated = observationTime.plusMinutes(3)
        val baseWeather = weatherDataFor(location)
        val weather = baseWeather.copy(
            current = baseWeather.current.copy(observationTime = observationTime),
            hourly = listOf(
                HourlyConditions(
                    time = observationTime,
                    temperature = 20.0,
                    feelsLike = null,
                    weatherCode = WeatherCode.CLEAR_SKY,
                    isDay = true,
                    precipitationProbability = 0,
                    precipitation = null,
                    windSpeed = null,
                    windDirection = null,
                    humidity = null,
                    uvIndex = null,
                    cloudCover = null,
                    visibility = null,
                ),
            ),
            lastUpdated = lastUpdated,
            sourceProvider = "Open-Meteo",
        )

        val widgetData = buildWidgetWeatherData(
            weatherData = weather,
            convertTemp = { celsius -> celsius * 9.0 / 5.0 + 32.0 },
            displayLocationName = "Pinned Boulder",
        )

        assertEquals("Pinned Boulder", widgetData.locationName)
        assertEquals("Open-Meteo", widgetData.sourceProvider)
        assertEquals(observationTime.toEpochMillisForTest(), widgetData.observedAt)
        assertEquals(lastUpdated.toEpochMillisForTest(), widgetData.updatedAt)
        assertEquals("Now", widgetData.hourly.single().hour)
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

    @Test
    fun `buildWidgetDailyItems caps rows at seven days`() {
        val today = LocalDate.of(2026, 4, 15)
        val daily = (0 until 9).map { offset ->
            DailyConditions(
                date = today.plusDays(offset.toLong()),
                weatherCode = WeatherCode.CLEAR_SKY,
                temperatureHigh = 20.0 + offset,
                temperatureLow = 10.0 + offset,
                precipitationProbability = offset,
                precipitationSum = null,
                sunrise = null,
                sunset = null,
                uvIndexMax = null,
                windSpeedMax = null,
                windDirectionDominant = null,
            )
        }

        val items = buildWidgetDailyItems(daily = daily, today = today) { it }

        assertEquals(7, items.size)
        assertEquals("Today", items.first().day)
        assertEquals(26, items.last().high)
    }

    private fun savedLocation(
        id: Long,
        name: String,
        sortOrder: Int,
        isCurrentLocation: Boolean = false,
    ): SavedLocationEntity {
        return SavedLocationEntity(
            id = id,
            name = name,
            latitude = 39.0 + id,
            longitude = -104.0 - id,
            sortOrder = sortOrder,
            isCurrentLocation = isCurrentLocation,
        )
    }

    private fun weatherDataFor(location: SavedLocationEntity): WeatherData {
        return WeatherData(
            location = LocationInfo(
                name = location.name,
                latitude = location.latitude,
                longitude = location.longitude,
            ),
            current = CurrentConditions(
                temperature = 20.0,
                feelsLike = 19.0,
                humidity = 40,
                weatherCode = WeatherCode.CLEAR_SKY,
                observationTime = LocalDateTime.of(2026, 4, 15, 12, 0),
                isDay = true,
                windSpeed = 8.0,
                windDirection = 180,
                windGusts = null,
                pressure = 1013.0,
                uvIndex = 4.0,
                visibility = 10_000.0,
                dewPoint = 8.0,
                cloudCover = 5,
                precipitation = 0.0,
                dailyHigh = 25.0,
                dailyLow = 10.0,
                sunrise = null,
                sunset = null,
            ),
            hourly = emptyList(),
            daily = emptyList(),
            lastUpdated = LocalDateTime.of(2026, 4, 15, 12, 0),
            sourceProvider = "Open-Meteo",
        )
    }

    private fun LocalDateTime.toEpochMillisForTest(): Long =
        atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
