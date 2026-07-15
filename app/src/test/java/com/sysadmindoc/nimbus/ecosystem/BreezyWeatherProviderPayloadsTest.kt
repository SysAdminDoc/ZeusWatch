package com.sysadmindoc.nimbus.ecosystem

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreezyWeatherProviderPayloadsTest {
    @Test
    fun savedLocationMapsToBreezyLocationColumns() {
        val location = SavedLocationEntity(
            id = 42L,
            name = "Denver",
            region = "Colorado",
            country = "United States",
            latitude = 39.7392,
            longitude = -104.9903,
            isCurrentLocation = false,
            timeZone = "America/Denver",
        )

        val row = location.toBreezyLocationRow()

        assertEquals("42", row.id)
        assertEquals(39.7392, row.latitude, 0.0001)
        assertEquals(-104.9903, row.longitude, 0.0001)
        assertEquals("America/Denver", row.timeZone)
        assertEquals("Denver", row.customName)
        assertEquals("Colorado", row.admin1)
        assertEquals("Denver", row.city)
        assertEquals("United States", row.country)
        assertNull(row.weather)
    }

    @Test
    fun currentPositionDoesNotExposeCustomName() {
        val location = SavedLocationEntity(
            id = 7L,
            name = "My Location",
            latitude = 47.6,
            longitude = -122.3,
            isCurrentLocation = true,
            timeZone = "America/Los_Angeles",
        )

        val row = location.toBreezyLocationRow()

        assertEquals("7", row.id)
        assertTrue(row.isCurrentPosition)
        assertNull(row.customName)
        assertEquals("My Location", row.city)
    }

    @Test
    fun weatherBlobUsesBreezyCompatibleJsonAndRequestedUnits() {
        val weather = sampleWeatherData()
        val units = BreezyUnitPreferences(
            temperatureUnit = "f",
            precipitationUnit = "in",
            speedUnit = "mph",
            distanceUnit = "mi",
            pressureUnit = "inhg",
        )

        val json = ungzip(weather.toBreezyWeatherBlob(units, refreshEpochMillis = 1_700_000_000_000L))
        val root = Json.parseToJsonElement(json).jsonObject
        val current = root.getValue("current").jsonObject
        val currentTemp = current.getValue("temperature").jsonObject
            .getValue("temperature").jsonObject
        val hourly = root.getValue("hourly").jsonArray.first().jsonObject
        val daily = root.getValue("daily").jsonArray.first().jsonObject

        assertEquals(1_700_000_000_000L.toString(), root.getValue("refreshTime").jsonPrimitive.content)
        assertEquals("Rain", current.getValue("weatherText").jsonPrimitive.content)
        assertEquals("63", current.getValue("weatherCode").jsonPrimitive.content)
        assertEquals("f", currentTemp.getValue("unit").jsonPrimitive.content)
        assertEquals(68.0, currentTemp.getValue("value").jsonPrimitive.double, 0.01)
        assertEquals(
            "inhg",
            current.getValue("pressure").jsonObject.getValue("unit").jsonPrimitive.content,
        )
        assertEquals(
            "mph",
            hourly.getValue("wind").jsonObject
                .getValue("speed").jsonObject
                .getValue("unit").jsonPrimitive.content,
        )
        assertEquals(
            "in",
            daily.getValue("day").jsonObject
                .getValue("precipitation").jsonObject
                .getValue("total").jsonObject
                .getValue("unit").jsonPrimitive.content,
        )
        assertEquals(
            "Open-Meteo",
            root.getValue("sources").jsonObject
                .getValue("forecast").jsonObject
                .getValue("text").jsonPrimitive.content,
        )
    }

    @Test
    fun snowfallConvertsFromCanonicalCmToMillimetres() {
        val json = snowyBlobJson(precipitationUnit = "mm")

        val hourlySnow = json.hourlySnow()
        val dailySnow = json.dailySnow()

        // Canonical snowfall is cm: 1.5 cm -> 15 mm, 2.54 cm -> 25.4 mm.
        assertEquals("mm", hourlySnow.getValue("unit").jsonPrimitive.content)
        assertEquals(15.0, hourlySnow.getValue("value").jsonPrimitive.double, 0.001)
        assertEquals("mm", dailySnow.getValue("unit").jsonPrimitive.content)
        assertEquals(25.4, dailySnow.getValue("value").jsonPrimitive.double, 0.001)
    }

    @Test
    fun snowfallConvertsFromCanonicalCmToInches() {
        val json = snowyBlobJson(precipitationUnit = "in")

        val dailySnow = json.dailySnow()

        // 2.54 cm = 25.4 mm = exactly 1 inch.
        assertEquals("in", dailySnow.getValue("unit").jsonPrimitive.content)
        assertEquals(1.0, dailySnow.getValue("value").jsonPrimitive.double, 0.001)
    }

    @Test
    fun snowfallPassesThroughUnchangedForCmUnit() {
        val json = snowyBlobJson(precipitationUnit = "cm")

        val hourlySnow = json.hourlySnow()

        // Already cm: the value must survive the mm round-trip unchanged.
        assertEquals("cm", hourlySnow.getValue("unit").jsonPrimitive.content)
        assertEquals(1.5, hourlySnow.getValue("value").jsonPrimitive.double, 0.001)
    }

    private fun snowyBlobJson(precipitationUnit: String): JsonObject {
        val base = sampleWeatherData()
        val weather = base.copy(
            hourly = listOf(base.hourly.first().copy(snowfall = 1.5)),
            daily = listOf(base.daily.first().copy(snowfallSum = 2.54)),
        )
        val units = BreezyUnitPreferences(
            temperatureUnit = "c",
            precipitationUnit = precipitationUnit,
            speedUnit = "kph",
            distanceUnit = "km",
            pressureUnit = "hpa",
        )
        val json = ungzip(weather.toBreezyWeatherBlob(units, refreshEpochMillis = 1_700_000_000_000L))
        return Json.parseToJsonElement(json).jsonObject
    }

    private fun JsonObject.hourlySnow(): JsonObject =
        getValue("hourly").jsonArray.first().jsonObject
            .getValue("precipitation").jsonObject
            .getValue("snow").jsonObject

    private fun JsonObject.dailySnow(): JsonObject =
        getValue("daily").jsonArray.first().jsonObject
            .getValue("day").jsonObject
            .getValue("precipitation").jsonObject
            .getValue("snow").jsonObject

    private fun sampleWeatherData(): WeatherData = WeatherData(
        location = LocationInfo(
            name = "Denver",
            region = "Colorado",
            country = "United States",
            latitude = 39.7392,
            longitude = -104.9903,
            timeZone = "America/Denver",
        ),
        current = CurrentConditions(
            temperature = 20.0,
            feelsLike = 19.0,
            humidity = 45,
            weatherCode = WeatherCode.RAIN_MODERATE,
            observationTime = LocalDateTime.of(2026, 1, 1, 12, 0),
            isDay = true,
            windSpeed = 18.0,
            windDirection = 270,
            windGusts = 30.0,
            pressure = 1013.25,
            uvIndex = 3.0,
            visibility = 10_000.0,
            dewPoint = 8.0,
            cloudCover = 80,
            precipitation = 1.2,
            dailyHigh = 23.0,
            dailyLow = 10.0,
            sunrise = "2026-01-01T07:20:00",
            sunset = "2026-01-01T16:55:00",
            sourceConditionText = "Rain",
        ),
        hourly = listOf(
            HourlyConditions(
                time = LocalDateTime.of(2026, 1, 1, 13, 0),
                temperature = 21.0,
                feelsLike = 20.5,
                weatherCode = WeatherCode.RAIN_SLIGHT,
                isDay = true,
                precipitationProbability = 60,
                precipitation = 0.8,
                windSpeed = 10.0,
                windDirection = 260,
                humidity = 48,
                uvIndex = 2.0,
                cloudCover = 75,
                visibility = 12_000.0,
                surfacePressure = 1010.0,
                sourceConditionText = "Light rain",
            ),
        ),
        daily = listOf(
            DailyConditions(
                date = LocalDate.of(2026, 1, 1),
                weatherCode = WeatherCode.RAIN_MODERATE,
                temperatureHigh = 23.0,
                temperatureLow = 10.0,
                precipitationProbability = 70,
                precipitationSum = 12.7,
                sunrise = "2026-01-01T07:20:00",
                sunset = "2026-01-01T16:55:00",
                uvIndexMax = 4.0,
                windSpeedMax = 28.0,
                windDirectionDominant = 270,
                precipitationHours = 4.0,
                sourceConditionText = "Rain",
            ),
        ),
        lastUpdated = LocalDateTime.of(2026, 1, 1, 12, 5),
        sourceProvider = "Open-Meteo",
    )

    private fun ungzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
