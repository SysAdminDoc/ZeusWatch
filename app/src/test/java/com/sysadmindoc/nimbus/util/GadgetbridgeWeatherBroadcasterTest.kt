package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GadgetbridgeWeatherBroadcasterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `payload uses WeatherSpec fields and gzipped multi-location array`() {
        val primary = weatherData(
            name = "Denver",
            latitude = 39.7392,
            longitude = -104.9903,
            timeZone = "America/Denver",
            code = WeatherCode.RAIN_MODERATE,
        )
        val secondary = weatherData(
            name = "Seattle",
            latitude = 47.6062,
            longitude = -122.3321,
            timeZone = "America/Los_Angeles",
            code = WeatherCode.CLEAR_SKY,
        )

        val payloads = buildGadgetbridgeWeatherPayloads(
            primary = primary,
            secondary = listOf(secondary),
            conditionLabel = { "Localized ${it.name}" },
        )

        val primaryJson = json.parseToJsonElement(payloads.primaryJson).jsonObject
        val secondaryJson = json.parseToJsonElement(payloads.secondaryJson).jsonArray
        val gzippedArray = json.parseToJsonElement(gunzip(payloads.weatherGz)).jsonArray
        val expectedTimestamp = primary.current.observationTime!!
            .atZone(ZoneId.of("America/Denver"))
            .toEpochSecond()
            .toInt()

        assertEquals("Denver", primaryJson["location"]!!.jsonPrimitive.content)
        assertEquals(293, primaryJson["currentTemp"]!!.jsonPrimitive.int)
        assertEquals(501, primaryJson["currentConditionCode"]!!.jsonPrimitive.int)
        assertEquals("Localized RAIN_MODERATE", primaryJson["currentCondition"]!!.jsonPrimitive.content)
        assertEquals(5.5, primaryJson["uvIndex"]!!.jsonPrimitive.double, 0.0)
        assertEquals(70, primaryJson["precipProbability"]!!.jsonPrimitive.int)
        assertEquals(285, primaryJson["dewPoint"]!!.jsonPrimitive.int)
        assertEquals(expectedTimestamp, primaryJson["timestamp"]!!.jsonPrimitive.int)
        assertEquals(1, primaryJson["forecasts"]!!.jsonArray.size)
        assertEquals(1, primaryJson["hourly"]!!.jsonArray.size)

        assertEquals(1, secondaryJson.size)
        assertEquals("Seattle", secondaryJson.first().jsonObject["location"]!!.jsonPrimitive.content)
        assertEquals(2, gzippedArray.size)
        assertEquals("Denver", gzippedArray.first().jsonObject["location"]!!.jsonPrimitive.content)
        assertEquals("Seattle", gzippedArray[1].jsonObject["location"]!!.jsonPrimitive.content)
    }

    @Test
    fun `payload coarsens coordinates to two decimals`() {
        // The Gadgetbridge action is a public broadcast: any installed app can
        // register for it, so full-precision GPS coordinates must never leak.
        val payloads = buildGadgetbridgeWeatherPayloads(
            primary = weatherData("Home", latitude = 39.123456, longitude = -104.987654),
        )

        val primaryJson = json.parseToJsonElement(payloads.primaryJson).jsonObject

        assertEquals(39.12, primaryJson["latitude"]!!.jsonPrimitive.double, 1e-4)
        assertEquals(-104.99, primaryJson["longitude"]!!.jsonPrimitive.double, 1e-4)
    }

    @Test
    fun `payload caps Gadgetbridge broadcasts to three locations`() {
        val payloads = buildGadgetbridgeWeatherPayloads(
            primary = weatherData("Primary"),
            secondary = listOf(
                weatherData("One"),
                weatherData("Two"),
                weatherData("Three"),
                weatherData("Four"),
            ),
        )

        val secondaryJson = json.parseToJsonElement(payloads.secondaryJson).jsonArray
        val allLocations = json.parseToJsonElement(gunzip(payloads.weatherGz)).jsonArray

        assertEquals(GADGETBRIDGE_MAX_LOCATIONS - 1, secondaryJson.size)
        assertEquals(GADGETBRIDGE_MAX_LOCATIONS, allLocations.size)
        assertTrue(allLocations.none { it.jsonObject["location"]!!.jsonPrimitive.content == "Three" })
    }

    private fun weatherData(
        name: String,
        latitude: Double = 39.0,
        longitude: Double = -104.0,
        timeZone: String? = "America/Denver",
        code: WeatherCode = WeatherCode.CLEAR_SKY,
    ): WeatherData {
        val now = LocalDateTime.of(2026, 1, 15, 12, 30)
        val today = LocalDate.of(2026, 1, 15)
        return WeatherData(
            location = LocationInfo(
                name = name,
                latitude = latitude,
                longitude = longitude,
                timeZone = timeZone,
            ),
            current = CurrentConditions(
                temperature = 20.0,
                feelsLike = 19.4,
                humidity = 42,
                weatherCode = code,
                observationTime = now,
                isDay = true,
                windSpeed = 16.0,
                windDirection = 225,
                windGusts = 25.0,
                pressure = 1012.5,
                uvIndex = 5.5,
                visibility = 14_000.0,
                dewPoint = 12.0,
                cloudCover = 35,
                precipitation = 0.4,
                dailyHigh = 25.0,
                dailyLow = 10.0,
                sunrise = "2026-01-15T07:12",
                sunset = "2026-01-15T17:08",
            ),
            hourly = listOf(
                HourlyConditions(
                    time = now.plusHours(1),
                    temperature = 21.0,
                    feelsLike = 20.0,
                    weatherCode = code,
                    isDay = true,
                    precipitationProbability = 70,
                    precipitation = 0.8,
                    windSpeed = 15.0,
                    windDirection = 230,
                    humidity = 44,
                    uvIndex = 5.0,
                    cloudCover = 40,
                    visibility = 12_000.0,
                ),
            ),
            daily = listOf(
                DailyConditions(
                    date = today,
                    weatherCode = code,
                    temperatureHigh = 25.0,
                    temperatureLow = 10.0,
                    precipitationProbability = 55,
                    precipitationSum = 3.0,
                    sunrise = "2026-01-15T07:12",
                    sunset = "2026-01-15T17:08",
                    uvIndexMax = 6.0,
                    windSpeedMax = 24.0,
                    windDirectionDominant = 220,
                ),
                DailyConditions(
                    date = today.plusDays(1),
                    weatherCode = code,
                    temperatureHigh = 23.0,
                    temperatureLow = 9.0,
                    precipitationProbability = 20,
                    precipitationSum = 1.0,
                    sunrise = "2026-01-16T07:11",
                    sunset = "2026-01-16T17:09",
                    uvIndexMax = 4.0,
                    windSpeedMax = 18.0,
                    windDirectionDominant = 180,
                ),
            ),
            lastUpdated = now,
        )
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
}
