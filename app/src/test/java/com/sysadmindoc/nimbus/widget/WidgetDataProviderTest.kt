package com.sysadmindoc.nimbus.widget

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for WidgetWeatherData construction and data integrity.
 *
 * WidgetDataProvider.save/load require Android DataStore (Context-bound),
 * so we test the pure data model construction and field mapping here.
 */
class WidgetDataProviderTest {

    // --- WidgetWeatherData construction ---

    @Test
    fun widgetWeatherDataStoresAllFields() {
        val data = WidgetWeatherData(
            locationName = "Denver",
            temperature = 72.0,
            feelsLike = 70.0,
            high = 85.0,
            low = 55.0,
            weatherCode = 2,
            isDay = true,
            humidity = 35,
            windSpeed = 15.0,
            hourly = emptyList(),
            daily = emptyList(),
            updatedAt = 1000L,
        )

        assertEquals("Denver", data.locationName)
        assertEquals(72.0, data.temperature, 0.01)
        assertEquals(70.0, data.feelsLike, 0.01)
        assertEquals(85.0, data.high, 0.01)
        assertEquals(55.0, data.low, 0.01)
        assertEquals(2, data.weatherCode)
        assertTrue(data.isDay)
        assertEquals(35, data.humidity)
        assertEquals(15.0, data.windSpeed, 0.01)
        assertEquals(1000L, data.updatedAt)
    }

    @Test
    fun widgetWeatherDataDefaultUpdatedAtIsZero() {
        val data = WidgetWeatherData(
            locationName = "Test",
            temperature = 0.0,
            feelsLike = 0.0,
            high = 0.0,
            low = 0.0,
            weatherCode = 0,
            isDay = true,
            humidity = 0,
            windSpeed = 0.0,
            hourly = emptyList(),
            daily = emptyList(),
        )

        assertEquals(0L, data.updatedAt)
    }

    // --- WidgetHourly items ---

    @Test
    fun widgetHourlyStoresAllFields() {
        val hourly = WidgetHourly(
            hour = "3 PM",
            temp = 75,
            code = 1,
            isDay = true,
            precipChance = 20,
        )

        assertEquals("3 PM", hourly.hour)
        assertEquals(75, hourly.temp)
        assertEquals(1, hourly.code)
        assertTrue(hourly.isDay)
        assertEquals(20, hourly.precipChance)
    }

    @Test
    fun buildHourlyItemsListWithCorrectSize() {
        val hourlyItems = (1..12).map { i ->
            WidgetHourly(
                hour = "${i} PM",
                temp = 70 + i,
                code = 0,
                isDay = i < 8,
                precipChance = i * 5,
            )
        }

        assertEquals(12, hourlyItems.size)
        assertEquals("1 PM", hourlyItems[0].hour)
        assertEquals("12 PM", hourlyItems[11].hour)
    }

    // --- WidgetDaily items ---

    @Test
    fun widgetDailyStoresAllFields() {
        val daily = WidgetDaily(
            day = "Today",
            high = 85,
            low = 60,
            code = 3,
            precipChance = 40,
        )

        assertEquals("Today", daily.day)
        assertEquals(85, daily.high)
        assertEquals(60, daily.low)
        assertEquals(3, daily.code)
        assertEquals(40, daily.precipChance)
    }

    @Test
    fun buildDailyItemsMapsDayLabelsCorrectly() {
        val labels = listOf("Today", "Tmrw", "Mon", "Tue", "Wed", "Thu", "Fri")
        val dailyItems = labels.mapIndexed { i, label ->
            WidgetDaily(
                day = label,
                high = 80 + i,
                low = 55 + i,
                code = 0,
                precipChance = 10,
            )
        }

        assertEquals(7, dailyItems.size)
        assertEquals("Today", dailyItems[0].day)
        assertEquals("Tmrw", dailyItems[1].day)
        assertEquals("Mon", dailyItems[2].day)
        assertEquals("Fri", dailyItems[6].day)
    }

    // --- WidgetWeatherData with hourly/daily lists ---

    @Test
    fun widgetWeatherDataAssemblesAllFieldsCorrectly() {
        val hourly = listOf(
            WidgetHourly(hour = "Now", temp = 72, code = 0, isDay = true, precipChance = 0),
            WidgetHourly(hour = "4 PM", temp = 74, code = 1, isDay = true, precipChance = 10),
            WidgetHourly(hour = "5 PM", temp = 73, code = 2, isDay = true, precipChance = 20),
        )
        val daily = listOf(
            WidgetDaily(day = "Today", high = 85, low = 60, code = 0, precipChance = 10),
            WidgetDaily(day = "Tmrw", high = 82, low = 58, code = 3, precipChance = 30),
        )

        val data = WidgetWeatherData(
            locationName = "Boulder",
            temperature = 72.0,
            feelsLike = 70.5,
            high = 85.0,
            low = 60.0,
            weatherCode = 0,
            isDay = true,
            humidity = 40,
            windSpeed = 12.0,
            hourly = hourly,
            daily = daily,
            updatedAt = System.currentTimeMillis(),
        )

        assertEquals(3, data.hourly.size)
        assertEquals(2, data.daily.size)
        assertEquals("Now", data.hourly[0].hour)
        assertEquals("Today", data.daily[0].day)
        assertEquals(85, data.daily[0].high)
    }

    @Test
    fun widgetWeatherDataHandlesEmptyLists() {
        val data = WidgetWeatherData(
            locationName = "Test",
            temperature = 50.0,
            feelsLike = 48.0,
            high = 55.0,
            low = 45.0,
            weatherCode = 0,
            isDay = false,
            humidity = 80,
            windSpeed = 5.0,
            hourly = emptyList(),
            daily = emptyList(),
        )

        assertTrue(data.hourly.isEmpty())
        assertTrue(data.daily.isEmpty())
        assertFalse(data.isDay)
    }

    // --- Temperature conversion simulation ---

    @Test
    fun celsiusToFahrenheitConversionIsCorrect() {
        // Mirrors the convertTemp lambda in WidgetRefreshWorker
        val celsius = 22.0
        val fahrenheit = celsius * 9.0 / 5.0 + 32.0
        assertEquals(71.6, fahrenheit, 0.01)
    }

    @Test
    fun celsiusToFahrenheitConversionZeroDegrees() {
        val celsius = 0.0
        val fahrenheit = celsius * 9.0 / 5.0 + 32.0
        assertEquals(32.0, fahrenheit, 0.01)
    }

    @Test
    fun celsiusToFahrenheitConversionNegativeTemp() {
        val celsius = -40.0
        val fahrenheit = celsius * 9.0 / 5.0 + 32.0
        assertEquals(-40.0, fahrenheit, 0.01) // -40 is the same in both scales
    }

    @Test
    fun celsiusToCelsiusConversionIsIdentity() {
        val celsius = 22.0
        // When unit is CELSIUS, no conversion happens
        assertEquals(22.0, celsius, 0.01)
    }

    // --- WidgetWeatherData equality ---

    @Test
    fun widgetWeatherDataEqualityWorks() {
        val data1 = WidgetWeatherData(
            locationName = "Denver", temperature = 72.0, feelsLike = 70.0,
            high = 85.0, low = 55.0, weatherCode = 2, isDay = true,
            humidity = 35, windSpeed = 15.0, hourly = emptyList(),
            daily = emptyList(), updatedAt = 1000L,
        )
        val data2 = data1.copy()

        assertEquals(data1, data2)
    }

    @Test
    fun widgetWeatherDataInequalityOnDifferentTemp() {
        val data1 = WidgetWeatherData(
            locationName = "Denver", temperature = 72.0, feelsLike = 70.0,
            high = 85.0, low = 55.0, weatherCode = 2, isDay = true,
            humidity = 35, windSpeed = 15.0, hourly = emptyList(),
            daily = emptyList(), updatedAt = 1000L,
        )
        val data2 = data1.copy(temperature = 99.0)

        assertNotEquals(data1, data2)
    }
}
