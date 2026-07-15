package com.sysadmindoc.nimbus.smartspacer

import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SmartspacerWeatherFormatterTest {

    private val strings = SmartspacerWeatherStrings(
        nextHourRain = { "Next hour rain $it%" },
        nextHourRainUnavailable = "Next-hour rain unavailable",
        currentLocation = "Current location",
    )

    private val denverZone = ZoneId.of("America/Denver")
    private val utcZone = ZoneId.of("UTC")

    @Test
    fun `builds current target and complication text from cached weather`() {
        val reference = LocalDateTime.of(2026, 7, 5, 12, 0)
        val snapshot = buildSmartspacerWeatherSnapshot(
            weather = weatherData(
                currentTempC = 22.2,
                conditionText = "Provider sunny",
                hourly = listOf(
                    hourly(reference.minusHours(1), 4),
                    hourly(reference.plusMinutes(30), 37),
                ),
            ),
            tempUnit = TempUnit.FAHRENHEIT,
            strings = strings,
            referenceInstant = reference.atZone(denverZone).toInstant(),
            fallbackZone = denverZone,
        )

        assertEquals("Denver 72\u00B0", snapshot.targetTitle)
        assertEquals("Provider sunny. Next hour rain 37%.", snapshot.targetSubtitle)
        assertEquals("72\u00B0", snapshot.complicationText)
        assertEquals(72, snapshot.temperature)
        assertEquals(false, snapshot.useCelsius)
        assertEquals(37, snapshot.nextHourPrecipitationProbability)
        assertEquals(R.drawable.ic_w_sunny, snapshot.iconRes)
        assertEquals(SmartspacerWeatherStateIcon.SUNNY, snapshot.stateIcon)
    }

    @Test
    fun `uses metric units and WMO condition fallback`() {
        val reference = LocalDateTime.of(2026, 7, 5, 12, 0)
        val snapshot = buildSmartspacerWeatherSnapshot(
            weather = weatherData(
                currentTempC = -3.4,
                weatherCode = WeatherCode.SNOW_MODERATE,
                isDay = false,
                conditionText = null,
                hourly = emptyList(),
            ),
            tempUnit = TempUnit.CELSIUS,
            strings = strings,
            referenceInstant = reference.atZone(denverZone).toInstant(),
            fallbackZone = denverZone,
        )

        assertEquals("Denver -3\u00B0", snapshot.targetTitle)
        assertEquals("Snow. Next-hour rain unavailable.", snapshot.targetSubtitle)
        assertEquals(true, snapshot.useCelsius)
        assertEquals(null, snapshot.nextHourPrecipitationProbability)
        assertEquals(R.drawable.ic_w_snow, snapshot.iconRes)
        assertEquals(SmartspacerWeatherStateIcon.SCATTERED_SNOW_SHOWERS_NIGHT, snapshot.stateIcon)
    }

    @Test
    fun `anchors next-hour selection in the location timezone, not the device zone`() {
        // Noon in Denver = 18:00 UTC. A device-zone anchor (UTC fallback) would
        // find no hourly row at/after 18:00 local and fall back to the first
        // row (4%); the location-zone anchor must pick the 12:30 row (37%).
        val denverNoon = LocalDateTime.of(2026, 7, 5, 12, 0)
        val snapshot = buildSmartspacerWeatherSnapshot(
            weather = weatherData(
                timeZone = "America/Denver",
                hourly = listOf(
                    hourly(denverNoon.minusHours(1), 4),
                    hourly(denverNoon.plusMinutes(30), 37),
                ),
            ),
            tempUnit = TempUnit.FAHRENHEIT,
            strings = strings,
            referenceInstant = denverNoon.atZone(denverZone).toInstant(),
            fallbackZone = utcZone,
        )

        assertEquals(37, snapshot.nextHourPrecipitationProbability)
    }

    @Test
    fun `falls back to device zone when the location timezone is missing or invalid`() {
        val utcNoon = LocalDateTime.of(2026, 7, 5, 12, 0)
        val snapshot = buildSmartspacerWeatherSnapshot(
            weather = weatherData(
                timeZone = "Not/AZone",
                hourly = listOf(
                    hourly(utcNoon.minusHours(1), 4),
                    hourly(utcNoon.plusMinutes(30), 37),
                ),
            ),
            tempUnit = TempUnit.FAHRENHEIT,
            strings = strings,
            referenceInstant = utcNoon.atZone(utcZone).toInstant(),
            fallbackZone = utcZone,
        )

        assertEquals(37, snapshot.nextHourPrecipitationProbability)
    }

    @Test
    fun `labels a blank location name with the current-location string`() {
        val reference = LocalDateTime.of(2026, 7, 5, 12, 0)
        val snapshot = buildSmartspacerWeatherSnapshot(
            weather = weatherData(locationName = "", currentTempC = 22.2),
            tempUnit = TempUnit.FAHRENHEIT,
            strings = strings,
            referenceInstant = reference.atZone(denverZone).toInstant(),
            fallbackZone = denverZone,
        )

        assertEquals("Current location 72\u00B0", snapshot.targetTitle)
    }

    private fun weatherData(
        currentTempC: Double = 20.0,
        weatherCode: WeatherCode = WeatherCode.CLEAR_SKY,
        isDay: Boolean = true,
        conditionText: String? = null,
        hourly: List<HourlyConditions> = emptyList(),
        locationName: String = "Denver",
        timeZone: String? = null,
    ): WeatherData = WeatherData(
        location = LocationInfo(
            name = locationName,
            region = "Colorado",
            country = "United States",
            latitude = 39.7,
            longitude = -104.9,
            timeZone = timeZone,
        ),
        current = CurrentConditions(
            temperature = currentTempC,
            feelsLike = currentTempC,
            humidity = 20,
            weatherCode = weatherCode,
            isDay = isDay,
            windSpeed = 10.0,
            windDirection = 180,
            windGusts = null,
            pressure = 1012.0,
            uvIndex = 4.0,
            visibility = null,
            dewPoint = null,
            cloudCover = 10,
            precipitation = 0.0,
            dailyHigh = currentTempC + 3,
            dailyLow = currentTempC - 3,
            sunrise = null,
            sunset = null,
            sourceConditionText = conditionText,
        ),
        hourly = hourly,
        daily = emptyList(),
        lastUpdated = LocalDateTime.of(2026, 7, 5, 11, 45),
        sourceProvider = "Open-Meteo",
    )

    private fun hourly(time: LocalDateTime, precipitationProbability: Int): HourlyConditions =
        HourlyConditions(
            time = time,
            temperature = 20.0,
            feelsLike = null,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            precipitationProbability = precipitationProbability,
            precipitation = null,
            windSpeed = null,
            windDirection = null,
            humidity = null,
            uvIndex = null,
            cloudCover = null,
            visibility = null,
        )
}
