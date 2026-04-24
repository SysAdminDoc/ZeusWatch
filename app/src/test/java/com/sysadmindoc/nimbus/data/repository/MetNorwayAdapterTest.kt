package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.MetNorwayApi
import com.sysadmindoc.nimbus.data.model.MetEntryData
import com.sysadmindoc.nimbus.data.model.MetInstant
import com.sysadmindoc.nimbus.data.model.MetInstantDetails
import com.sysadmindoc.nimbus.data.model.MetNorwayResponse
import com.sysadmindoc.nimbus.data.model.MetPeriod
import com.sysadmindoc.nimbus.data.model.MetPeriodDetails
import com.sysadmindoc.nimbus.data.model.MetProperties
import com.sysadmindoc.nimbus.data.model.MetSummary
import com.sysadmindoc.nimbus.data.model.MetTimeseriesEntry
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
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MetNorwayAdapterTest {

    private fun buildEntry(
        hourOffset: Long,
        temp: Double,
        windSpeedMs: Double? = null,
        symbol: String? = null,
        precipMm: Double? = null,
        probOfPrecip: Double? = null,
        gustsMs: Double? = null,
    ) = MetTimeseriesEntry(
        time = OffsetDateTime.now(ZoneOffset.UTC).plusHours(hourOffset)
            .withMinute(0).withSecond(0).withNano(0)
            .toString(),
        data = MetEntryData(
            instant = MetInstant(
                details = MetInstantDetails(
                    airTemperature = temp,
                    relativeHumidity = 70.0,
                    airPressureAtSeaLevel = 1012.0,
                    cloudAreaFraction = 40.0,
                    windSpeed = windSpeedMs,
                    windFromDirection = 180.0,
                    windSpeedOfGust = gustsMs,
                    ultravioletIndexClearSky = 2.5,
                    dewPointTemperature = temp - 4.0,
                ),
            ),
            next1Hours = symbol?.let {
                MetPeriod(
                    summary = MetSummary(symbolCode = it),
                    details = MetPeriodDetails(
                        precipitationAmount = precipMm,
                        probabilityOfPrecipitation = probOfPrecip,
                    ),
                )
            },
        ),
    )

    @Test
    fun `happy path maps current + hourly + aggregated daily`() = runTest {
        val api = mockk<MetNorwayApi>()
        val adapter = MetNorwayForecastAdapter(api)
        val entries = (0L..23L).map { h ->
            buildEntry(
                hourOffset = h,
                temp = 10.0 + h.toDouble(),
                windSpeedMs = 3.0,                  // 3 m/s → 10.8 km/h
                symbol = "partlycloudy_day",
                precipMm = 0.0,
                probOfPrecip = 0.0,
                gustsMs = 5.0,
            )
        }
        coEvery { api.getForecast(any(), any(), any()) } returns MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = entries),
        )

        val data = adapter.getWeather(60.39, 5.32, "Bergen").getOrThrow()

        // Current conditions
        assertEquals("Bergen", data.location.name)
        assertEquals(WeatherCode.PARTLY_CLOUDY, data.current.weatherCode)
        assertEquals(70, data.current.humidity)
        // m/s → km/h conversion
        assertEquals(10.8, data.current.windSpeed, 0.01)
        assertEquals(18.0, data.current.windGusts!!, 0.01)
        assertEquals(180, data.current.windDirection)
        assertEquals(40, data.current.cloudCover)

        // Hourly is populated
        assertTrue("hourly should not be empty", data.hourly.isNotEmpty())
        assertEquals(WeatherCode.PARTLY_CLOUDY, data.hourly.first().weatherCode)

        // Daily aggregation
        assertTrue("daily should not be empty", data.daily.isNotEmpty())
        val firstDay = data.daily.first()
        assertTrue(firstDay.temperatureHigh >= firstDay.temperatureLow)
    }

    @Test
    fun `missing instant details default to safe zeros without crashing`() = runTest {
        val api = mockk<MetNorwayApi>()
        val adapter = MetNorwayForecastAdapter(api)
        // Entry with NO instant details at all — simulates sparse forecast
        // data where MET omits fields past the 2.5-day mark.
        val sparseEntry = MetTimeseriesEntry(
            time = OffsetDateTime.now(ZoneOffset.UTC).toString(),
            data = MetEntryData(instant = MetInstant(details = null)),
        )
        coEvery { api.getForecast(any(), any(), any()) } returns MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = listOf(sparseEntry)),
        )

        val data = adapter.getWeather(60.39, 5.32, null).getOrThrow()
        assertEquals(0.0, data.current.temperature, 0.0001)
        assertEquals(0, data.current.humidity)
        assertEquals(0.0, data.current.windSpeed, 0.0001)
        assertNull(data.current.windGusts)
        // MET doesn't publish feels-like or visibility — expect safe defaults
        assertNull(data.current.visibility)
        assertEquals(data.current.temperature, data.current.feelsLike, 0.0001)
    }

    @Test
    fun `empty timeseries returns a failed Result, not a crash`() = runTest {
        val api = mockk<MetNorwayApi>()
        val adapter = MetNorwayForecastAdapter(api)
        coEvery { api.getForecast(any(), any(), any()) } returns MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = emptyList()),
        )

        val result = adapter.getWeather(60.39, 5.32, null)
        assertTrue("must surface failure, not crash", result.isFailure)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `symbol mapper translates common MET codes to WMO`() {
        assertEquals(0, MetSymbolMapper.toWmoCode("clearsky_day"))
        assertEquals(1, MetSymbolMapper.toWmoCode("fair_night"))
        assertEquals(2, MetSymbolMapper.toWmoCode("partlycloudy_day"))
        assertEquals(3, MetSymbolMapper.toWmoCode("cloudy"))
        assertEquals(63, MetSymbolMapper.toWmoCode("rain"))
        assertEquals(65, MetSymbolMapper.toWmoCode("heavyrain"))
        assertEquals(95, MetSymbolMapper.toWmoCode("rainandthunder"))
        assertEquals(73, MetSymbolMapper.toWmoCode("snow"))
        assertEquals(45, MetSymbolMapper.toWmoCode("fog"))
        // Unknown code gracefully degrades to 0 (clear sky) — not a crash
        assertEquals(0, MetSymbolMapper.toWmoCode("some_unknown_code"))
    }

    @Test
    fun `is-day detection uses symbol suffix when present`() {
        val time = OffsetDateTime.parse("2026-06-21T23:00:00+00:00").toLocalDateTime()
        assertTrue(MetSymbolMapper.isDayFromSymbol("clearsky_day", time))
        assertFalse(MetSymbolMapper.isDayFromSymbol("cloudy_night", time))
        assertFalse(MetSymbolMapper.isDayFromSymbol("partlycloudy_polartwilight", time))
        // Fallback to hour-of-day when symbol omits suffix — 23:00 is night
        assertFalse(MetSymbolMapper.isDayFromSymbol("rain", time))
        assertNotNull(MetSymbolMapper.isDayFromSymbol(null, time))
    }
}
