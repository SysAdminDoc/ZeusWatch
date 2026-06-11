package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.BrightSkyApi
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.BrightSkyAlertResponse
import com.sysadmindoc.nimbus.data.model.BrightSkyWeatherResponse
import com.sysadmindoc.nimbus.data.model.BsAlert
import com.sysadmindoc.nimbus.data.model.BsSource
import com.sysadmindoc.nimbus.data.model.BsWeatherEntry
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun makeWeatherEntry(
    timestamp: String,
    temp: Double = 15.0,
    condition: String? = null,
    icon: String? = "clear-day",
    humidity: Double? = 60.0,
    windSpeed: Double? = 20.0, // km/h
    windDirection: Int? = 180,
    windGustSpeed: Double? = null,
    precipitationProbability: Double? = null,
    precipitation: Double? = null,
    visibility: Double? = 10000.0,
    cloudCover: Double? = 20.0,
    sunshine: Double? = null, // minutes in last hour
) = BsWeatherEntry(
    timestamp = timestamp,
    temperature = temp,
    condition = condition,
    icon = icon,
    humidity = humidity,
    windSpeed = windSpeed,
    windDirection = windDirection,
    windGustSpeed = windGustSpeed,
    precipitationProbability = precipitationProbability,
    precipitation = precipitation,
    visibility = visibility,
    cloudCover = cloudCover,
    sunshine = sunshine,
    pressureMsl = 1013.0,
    dewPoint = 8.0,
)

class BrightSkyForecastAdapterTest {

    // BrightSky timestamps must be offset-aware ISO 8601
    private val baseTs = "2026-06-15T12:00:00+00:00"
    private val hour1Ts = "2026-06-15T13:00:00+00:00"
    private val hour2Ts = "2026-06-15T14:00:00+00:00"
    private val tomorrowTs = "2026-06-16T12:00:00+00:00"

    @Test
    fun `happy path — maps station name and basic current conditions`() = runTest {
        val api = mockk<BrightSkyApi>()
        val entry = makeWeatherEntry(timestamp = baseTs, temp = 22.0, windSpeed = 30.0)
        val response = BrightSkyWeatherResponse(
            weather = listOf(entry),
            sources = listOf(BsSource(id = 1, stationName = "Hamburg")),
        )
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val result = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Hamburg", data.location.name)
        assertEquals(22.0, data.current.temperature, 0.01)
        assertEquals(30.0, data.current.windSpeed, 0.01) // already km/h — no conversion
    }

    @Test
    fun `custom location name overrides station name`() = runTest {
        val api = mockk<BrightSkyApi>()
        val response = BrightSkyWeatherResponse(
            weather = listOf(makeWeatherEntry(baseTs)),
            sources = listOf(BsSource(id = 1, stationName = "DWD Station")),
        )
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, "My Home").getOrThrow()
        assertEquals("My Home", data.location.name)
    }

    @Test
    fun `rain condition maps to RAIN WMO code`() = runTest {
        val api = mockk<BrightSkyApi>()
        val entry = makeWeatherEntry(baseTs, condition = "rain", icon = null)
        val response = BrightSkyWeatherResponse(weather = listOf(entry))
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertEquals(WeatherCode.RAIN_MODERATE, data.current.weatherCode)
    }

    @Test
    fun `thunderstorm condition maps to THUNDERSTORM WMO code`() = runTest {
        val api = mockk<BrightSkyApi>()
        val entry = makeWeatherEntry(baseTs, condition = "thunderstorm", icon = null)
        val response = BrightSkyWeatherResponse(weather = listOf(entry))
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertEquals(WeatherCode.THUNDERSTORM, data.current.weatherCode)
    }

    @Test
    fun `clear-day icon sets isDay true, clear-night sets isDay false`() = runTest {
        val api = mockk<BrightSkyApi>()

        val dayEntry = makeWeatherEntry(baseTs, icon = "clear-day")
        val dayResp = BrightSkyWeatherResponse(weather = listOf(dayEntry))
        coEvery { api.getWeather(any(), any(), any(), any()) } returns dayResp
        coEvery { api.getCurrentWeather(any(), any()) } returns dayResp

        val dayData = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertTrue(dayData.current.isDay)

        val nightEntry = makeWeatherEntry(baseTs, icon = "clear-night")
        val nightResp = BrightSkyWeatherResponse(weather = listOf(nightEntry))
        coEvery { api.getWeather(any(), any(), any(), any()) } returns nightResp
        coEvery { api.getCurrentWeather(any(), any()) } returns nightResp

        val nightData = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertFalse(nightData.current.isDay)
    }

    @Test
    fun `visibility is stored as meters for WeatherFormatter contract`() = runTest {
        val api = mockk<BrightSkyApi>()
        val entry = makeWeatherEntry(baseTs, visibility = 8000.0) // DWD sends meters
        val response = BrightSkyWeatherResponse(weather = listOf(entry))
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertEquals(8000.0, data.current.visibility!!, 0.01)
    }

    @Test
    fun `daily aggregation produces high and low from hourly entries`() = runTest {
        val api = mockk<BrightSkyApi>()
        val entries = listOf(
            makeWeatherEntry(baseTs, temp = 10.0),
            makeWeatherEntry(hour1Ts, temp = 22.0),
            makeWeatherEntry(hour2Ts, temp = 18.0),
        )
        val response = BrightSkyWeatherResponse(weather = entries)
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        val today = data.daily.firstOrNull()
        assertNotNull("daily should be non-empty", today)
        assertEquals(22.0, today!!.temperatureHigh, 0.01)
        assertEquals(10.0, today.temperatureLow, 0.01)
    }

    @Test
    fun `multiple-day entries produce distinct daily entries sorted by date`() = runTest {
        val api = mockk<BrightSkyApi>()
        val entries = listOf(
            makeWeatherEntry(baseTs, temp = 15.0),     // June 15
            makeWeatherEntry(tomorrowTs, temp = 20.0), // June 16
        )
        val response = BrightSkyWeatherResponse(weather = entries)
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertEquals(2, data.daily.size)
        assertTrue("Days should be sorted ascending", data.daily[0].date.isBefore(data.daily[1].date))
    }

    @Test
    fun `circular wind mean handles 350 and 10 degree mix correctly`() = runTest {
        // North-ish directions: 350° and 10° — circular mean should be ~0° (north), not ~180°
        val api = mockk<BrightSkyApi>()
        val entries = listOf(
            makeWeatherEntry(baseTs, windDirection = 350),
            makeWeatherEntry(hour1Ts, windDirection = 10),
        )
        val response = BrightSkyWeatherResponse(weather = entries)
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        val dominant = data.daily.firstOrNull()?.windDirectionDominant
        assertNotNull("wind direction should be set", dominant)
        // Circular mean of 350° and 10° should be near 0°/360°
        val northish = dominant!! <= 20 || dominant >= 340
        assertTrue("Expected north-ish direction, got $dominant°", northish)
    }

    @Test
    fun `sunshine minutes convert to seconds in hourly and daily`() = runTest {
        // DWD reports sunshine in minutes; the formatter contract is seconds.
        val api = mockk<BrightSkyApi>()
        val entries = listOf(
            makeWeatherEntry(baseTs, sunshine = 30.0),
            makeWeatherEntry(hour1Ts, sunshine = 45.0),
        )
        val response = BrightSkyWeatherResponse(weather = entries)
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertEquals(30.0 * 60, data.hourly.first().sunshineDuration!!, 0.01)
        assertEquals(45.0 * 60, data.hourly[1].sunshineDuration!!, 0.01)
        assertEquals(75.0 * 60, data.daily.first().sunshineDuration!!, 0.01)
    }

    @Test
    fun `daily drops the yesterday padding day from the fetch window`() = runTest {
        // The fetch window deliberately starts at yesterday UTC (timezone
        // safety); the aggregated daily list must not surface that stale day
        // as daily[0], which "today" consumers read via daily.firstOrNull().
        val api = mockk<BrightSkyApi>()
        val yesterdayTs = "2026-06-14T12:00:00+00:00"
        val forecast = BrightSkyWeatherResponse(
            weather = listOf(
                makeWeatherEntry(yesterdayTs, temp = 5.0),
                makeWeatherEntry(baseTs, temp = 15.0),
                makeWeatherEntry(tomorrowTs, temp = 20.0),
            ),
        )
        val current = BrightSkyWeatherResponse(weather = listOf(makeWeatherEntry(baseTs, temp = 15.0)))
        coEvery { api.getWeather(any(), any(), any(), any()) } returns forecast
        coEvery { api.getCurrentWeather(any(), any()) } returns current

        val data = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null).getOrThrow()
        assertEquals(2, data.daily.size)
        assertEquals(15.0, data.daily.first().temperatureHigh, 0.01)
        assertTrue(
            "No daily entry may predate today (${data.daily.first().date})",
            data.daily.none { it.date.isBefore(data.daily.first().date) },
        )
    }

    @Test
    fun `empty weather list returns failure`() = runTest {
        val api = mockk<BrightSkyApi>()
        val response = BrightSkyWeatherResponse(weather = emptyList())
        coEvery { api.getWeather(any(), any(), any(), any()) } returns response
        coEvery { api.getCurrentWeather(any(), any()) } returns response

        val result = BrightSkyForecastAdapter(api).getWeather(53.5, 10.0, null)
        assertTrue("Empty list must yield failure", result.isFailure)
    }
}

class BrightSkyAlertAdapterTest {

    private val nowIso = "2026-06-15T12:00:00+00:00"
    private val expiresIso = "2026-06-15T18:00:00+00:00"

    @Test
    fun `English event preferred over German`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(
            alerts = listOf(
                BsAlert(
                    id = 1,
                    alertId = "dwd-test-001",
                    eventEn = "Severe Thunderstorm Warning",
                    eventDe = "Schweres Gewitter-Warnung",
                    severity = "Severe",
                    urgency = "Immediate",
                    effective = nowIso,
                    expires = expiresIso,
                )
            )
        )

        val result = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4)
        assertTrue(result.isSuccess)
        val alert = result.getOrThrow().first()
        assertEquals("Severe Thunderstorm Warning", alert.event)
    }

    @Test
    fun `German event used as fallback when English is absent`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(
            alerts = listOf(
                BsAlert(
                    id = 2,
                    alertId = "dwd-test-002",
                    eventEn = null,
                    eventDe = "Unwetterwarnung",
                    severity = "Moderate",
                    urgency = "Expected",
                    effective = nowIso,
                    expires = expiresIso,
                )
            )
        )

        val result = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4)
        assertEquals("Unwetterwarnung", result.getOrThrow().first().event)
    }

    @Test
    fun `blank alertId falls back to dwd-{id}`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(
            alerts = listOf(
                BsAlert(
                    id = 42,
                    alertId = "", // blank — should fall back to "dwd-42"
                    eventEn = "Wind Warning",
                    severity = "Minor",
                )
            )
        )

        val alert = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4).getOrThrow().first()
        assertEquals("dwd-42", alert.id)
    }

    @Test
    fun `English description preferred over German`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(
            alerts = listOf(
                BsAlert(
                    id = 3,
                    alertId = "dwd-test-003",
                    eventEn = "Rain",
                    descriptionEn = "Heavy rain expected",
                    descriptionDe = "Starkregen erwartet",
                    severity = "Moderate",
                )
            )
        )

        val alert = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4).getOrThrow().first()
        assertEquals("Heavy rain expected", alert.description)
    }

    @Test
    fun `severity string maps to AlertSeverity enum`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(
            alerts = listOf(
                BsAlert(id = 4, alertId = "s1", eventEn = "Frost", severity = "Extreme"),
                BsAlert(id = 5, alertId = "s2", eventEn = "Ice", severity = "Minor"),
            )
        )

        val alerts = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4).getOrThrow()
        assertEquals(AlertSeverity.EXTREME, alerts[0].severity)
        assertEquals(AlertSeverity.MINOR, alerts[1].severity)
    }

    @Test
    fun `senderName is always DWD regardless of alert content`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(
            alerts = listOf(BsAlert(id = 1, alertId = "x", eventEn = "Fog"))
        )

        val alert = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4).getOrThrow().first()
        assertEquals("DWD", alert.senderName)
    }

    @Test
    fun `empty alert list returns empty success`() = runTest {
        val api = mockk<BrightSkyApi>()
        coEvery { api.getAlerts(any(), any()) } returns BrightSkyAlertResponse(alerts = emptyList())

        val result = BrightSkyAlertAdapter(api).getAlerts(52.5, 13.4)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
