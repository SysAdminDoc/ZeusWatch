package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.EcccAbbreviatedForecast
import com.sysadmindoc.nimbus.data.api.EcccCurrentConditions
import com.sysadmindoc.nimbus.data.api.EcccFeature
import com.sysadmindoc.nimbus.data.api.EcccFeatureCollection
import com.sysadmindoc.nimbus.data.api.EcccForecastEntry
import com.sysadmindoc.nimbus.data.api.EcccForecastGroup
import com.sysadmindoc.nimbus.data.api.EcccGeometry
import com.sysadmindoc.nimbus.data.api.EcccProperties
import com.sysadmindoc.nimbus.data.api.EcccTemperature
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaForecastApi
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentCanadaForecastAdapterTest {

    private fun feature(
        lat: Double,
        lon: Double,
        cityEn: String = "Toronto",
        iconCode: String? = "03",
        temperature: Double = 12.5,
        humidity: Double = 68.0,
        pressureKpa: Double = 101.3,
        windKmh: Double = 15.0,
        forecast: List<EcccForecastEntry> = defaultForecast(),
    ): EcccFeature = EcccFeature(
        id = "ecccTest",
        type = "Feature",
        geometry = EcccGeometry(type = "Point", coordinates = listOf(lon, lat)),
        properties = EcccProperties(
            cityEn = cityEn,
            nameEn = cityEn,
            timestampUtc = "2026-04-24T12:00:00+00:00",
            currentConditions = EcccCurrentConditions(
                condition = "Partly cloudy",
                iconCode = iconCode?.let { JsonPrimitive(it) },
                temperatureValue = temperature,
                relativeHumidityValue = humidity,
                pressureValue = pressureKpa,
                dewpointValue = 7.5,
                visibilityValue = 24.0,
                windSpeedValue = windKmh,
                windBearingValue = 230.0,
                windDirectionValue = "SW",
                windGustValue = 25.0,
                windChillValue = null,
                humidexValue = null,
                observationDateTimeUtc = "2026-04-24T12:00:00+00:00",
            ),
            forecastGroup = EcccForecastGroup(forecast = forecast),
        ),
    )

    private fun defaultForecast(): List<EcccForecastEntry> = listOf(
        EcccForecastEntry(
            period = "Today",
            textSummary = "Partly cloudy. High 15.",
            temperatures = listOf(EcccTemperature(tempClass = "high", value = 15.0)),
            abbreviatedForecast = EcccAbbreviatedForecast(
                iconCode = JsonPrimitive("03"),
                textSummary = "Partly cloudy",
                pop = 20.0,
            ),
        ),
        EcccForecastEntry(
            period = "Tonight",
            textSummary = "Cloudy periods. Low 5.",
            temperatures = listOf(EcccTemperature(tempClass = "low", value = 5.0)),
            abbreviatedForecast = EcccAbbreviatedForecast(
                iconCode = JsonPrimitive("04"),
                textSummary = "Cloudy periods",
                pop = 10.0,
            ),
        ),
        EcccForecastEntry(
            period = "Friday",
            textSummary = "Rain. High 9.",
            temperatures = listOf(EcccTemperature(tempClass = "high", value = 9.0)),
            abbreviatedForecast = EcccAbbreviatedForecast(
                iconCode = JsonPrimitive("12"),
                textSummary = "Rain",
                pop = 80.0,
            ),
        ),
        EcccForecastEntry(
            period = "Friday night",
            textSummary = "Clearing. Low 2.",
            temperatures = listOf(EcccTemperature(tempClass = "low", value = 2.0)),
            abbreviatedForecast = EcccAbbreviatedForecast(
                iconCode = JsonPrimitive("01"),
                textSummary = "Clear",
                pop = 0.0,
            ),
        ),
    )

    @Test
    fun `happy path maps nearest feature and converts units`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        // Two cities; Toronto is much closer to the query point.
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            type = "FeatureCollection",
            features = listOf(
                feature(lat = 43.65, lon = -79.38, cityEn = "Toronto"),
                feature(lat = 45.42, lon = -75.69, cityEn = "Ottawa"),
            ),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val data = adapter.getWeather(43.70, -79.40, null).getOrThrow()

        assertEquals("Toronto", data.location.name)
        assertEquals("CA", data.location.country)
        assertEquals(12.5, data.current.temperature, 0.0001)
        assertEquals(68, data.current.humidity)
        // kPa → hPa conversion
        assertEquals(1013.0, data.current.pressure, 0.01)
        // km → m conversion on visibility
        assertEquals(24_000.0, data.current.visibility!!, 0.01)
        // Hourly is intentionally empty for ECCC free tier
        assertTrue(data.hourly.isEmpty())
        // Daily was paired into Today (high 15 / low 5) + Friday (high 9 / low 2)
        assertEquals(2, data.daily.size)
        assertEquals(15.0, data.daily[0].temperatureHigh, 0.0001)
        assertEquals(5.0, data.daily[0].temperatureLow, 0.0001)
        assertEquals(80, data.daily[1].precipitationProbability)
    }

    @Test
    fun `empty feature collection surfaces a failure instead of crashing`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        // Both the tight and the widened bbox queries return nothing.
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            features = emptyList(),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val result = adapter.getWeather(70.0, -90.0, null)
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `icon code maps to a WMO weather code via companion`() {
        // Partly cloudy (iconCode 03) → WMO 2
        val cc = EcccCurrentConditions(iconCode = JsonPrimitive("03"), temperatureValue = 10.0)
        assertEquals(2, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(cc))

        // Night-side partly cloudy (iconCode 33 = 3 + 30) should land on the
        // same day-side bucket per the %30 fold.
        val ccNight = EcccCurrentConditions(iconCode = JsonPrimitive("33"), temperatureValue = 10.0)
        assertEquals(2, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(ccNight))

        // Rain → 65
        val rain = EcccCurrentConditions(iconCode = JsonPrimitive("12"), temperatureValue = 10.0)
        assertEquals(65, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(rain))

        // Thunderstorm → 95
        val thunder = EcccCurrentConditions(iconCode = JsonPrimitive("19"), temperatureValue = 10.0)
        assertEquals(95, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(thunder))
    }

    @Test
    fun `condition text fallback fires when icon is absent`() {
        val ccText = EcccCurrentConditions(
            condition = "Freezing rain",
            temperatureValue = 0.0,
        )
        assertEquals(66, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(ccText))

        val ccClear = EcccCurrentConditions(condition = "Sunny", temperatureValue = 20.0)
        assertEquals(0, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(ccClear))
    }

    @Test
    fun `abbreviated forecast icon maps through the same table`() {
        val af = EcccAbbreviatedForecast(
            iconCode = JsonPrimitive("14"),
            textSummary = "Heavy snow",
        )
        // iconCode 14 → 75 (heavy snow)
        assertEquals(75, EnvironmentCanadaForecastAdapter.mapAbbreviatedToWmo(af))
    }

    @Test
    fun `null and malformed icon codes degrade to condition text`() {
        val cc = EcccCurrentConditions(
            condition = "Blizzard conditions",
            iconCode = JsonPrimitive("not-a-number"),
            temperatureValue = -15.0,
        )
        assertEquals(99, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(cc))

        // A null iconCode + null condition yields clear-sky default (0)
        val unknown = EcccCurrentConditions(temperatureValue = 20.0)
        assertEquals(0, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(unknown))
    }

    @Test
    fun `mapping returns a valid WeatherCode enum even for unknown codes`() {
        val code = EnvironmentCanadaForecastAdapter.mapCurrentToWmo(
            EcccCurrentConditions(condition = "Ice fog", temperatureValue = -20.0)
        )
        // Ice fog isn't in the pattern table — falls through to default 0
        // but WeatherCode.fromCode(0) must still resolve to CLEAR_SKY safely.
        assertNotNull(WeatherCode.fromCode(code))
    }
}
