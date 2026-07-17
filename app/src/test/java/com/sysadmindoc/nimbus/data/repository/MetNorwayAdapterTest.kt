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
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
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
        val adapter = MetNorwayForecastAdapter(api, MetNorwayHttpCache())
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
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = entries),
        ))

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
        val adapter = MetNorwayForecastAdapter(api, MetNorwayHttpCache())
        // Entry with NO instant details at all — simulates sparse forecast
        // data where MET omits fields past the 2.5-day mark.
        val sparseEntry = MetTimeseriesEntry(
            time = OffsetDateTime.now(ZoneOffset.UTC).toString(),
            data = MetEntryData(instant = MetInstant(details = null)),
        )
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = listOf(sparseEntry)),
        ))

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
        val adapter = MetNorwayForecastAdapter(api, MetNorwayHttpCache())
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = emptyList()),
        ))

        val result = adapter.getWeather(60.39, 5.32, null)
        assertTrue("must surface failure, not crash", result.isFailure)
        assertFalse(result.isSuccess)
    }

    /** Fixed-time entry for deterministic daily-aggregation tests. */
    private fun entryAt(
        time: String,
        next1PrecipMm: Double? = null,
        next6PrecipMm: Double? = null,
    ) = MetTimeseriesEntry(
        time = time,
        data = MetEntryData(
            instant = MetInstant(details = MetInstantDetails(airTemperature = 10.0)),
            next1Hours = next1PrecipMm?.let {
                MetPeriod(
                    summary = MetSummary(symbolCode = "rain"),
                    details = MetPeriodDetails(precipitationAmount = it),
                )
            },
            next6Hours = next6PrecipMm?.let {
                MetPeriod(
                    summary = MetSummary(symbolCode = "rain"),
                    details = MetPeriodDetails(precipitationAmount = it),
                )
            },
        ),
    )

    @Test
    fun `daily precip includes next6Hours blocks where next1Hours is absent`() {
        // MET degrades to 6-hourly periods after ~48h: those entries carry
        // only next_6_hours. They must contribute to the daily sum — and an
        // overlapping next_6_hours on an hour already covered by
        // next_1_hours must NOT double-count.
        val adapter = MetNorwayForecastAdapter(mockk(), MetNorwayHttpCache())
        val entries = listOf(
            // Hourly resolution 00:00–05:00 (first entry also carries an
            // overlapping 6h block that must be ignored).
            entryAt("2026-06-15T00:00:00Z", next1PrecipMm = 1.0, next6PrecipMm = 9.0),
            entryAt("2026-06-15T01:00:00Z", next1PrecipMm = 1.0),
            entryAt("2026-06-15T02:00:00Z", next1PrecipMm = 1.0),
            entryAt("2026-06-15T03:00:00Z", next1PrecipMm = 1.0),
            entryAt("2026-06-15T04:00:00Z", next1PrecipMm = 1.0),
            entryAt("2026-06-15T05:00:00Z", next1PrecipMm = 1.0),
            // Degraded 6-hourly resolution for the rest of the day.
            entryAt("2026-06-15T06:00:00Z", next6PrecipMm = 5.0),
            entryAt("2026-06-15T12:00:00Z", next6PrecipMm = 5.0),
            entryAt("2026-06-15T18:00:00Z", next6PrecipMm = 5.0),
        )
        val data = adapter.mapToWeatherData(
            MetNorwayResponse(type = "Feature", properties = MetProperties(timeseries = entries)),
            60.39, 5.32, null, ZoneId.of("UTC"),
        )
        val day = data.daily.first { it.date == LocalDate.of(2026, 6, 15) }
        // 6 × 1.0 mm (hourly) + 3 × 5.0 mm (6-hourly) = 21.0 mm
        assertEquals(21.0, day.precipitationSum ?: -1.0, 0.001)
    }

    @Test
    fun `overlapping next6Hours-only windows are counted once`() {
        // Transition region: hourly entries that each repeat a rolling 6h
        // block. Only non-overlapping stretches may be summed.
        val adapter = MetNorwayForecastAdapter(mockk(), MetNorwayHttpCache())
        val entries = (0L..5L).map { h ->
            entryAt("2026-06-15T0$h:00:00Z", next6PrecipMm = 6.0)
        }
        val data = adapter.mapToWeatherData(
            MetNorwayResponse(type = "Feature", properties = MetProperties(timeseries = entries)),
            60.39, 5.32, null, ZoneId.of("UTC"),
        )
        val day = data.daily.first { it.date == LocalDate.of(2026, 6, 15) }
        assertEquals(6.0, day.precipitationSum ?: -1.0, 0.001)
    }

    @Test
    fun `daily extrema fold in modeled six-hour min and max temps`() {
        // Day 3+ degradation: entries every 6 hours carry only an instant
        // sample plus next_6_hours blocks. The instant samples miss the true
        // extrema between samples — the modeled air_temperature_max/min must
        // widen the daily high/low.
        val adapter = MetNorwayForecastAdapter(mockk(), MetNorwayHttpCache())
        val entries = listOf(0L, 6L, 12L, 18L).map { hour ->
            MetTimeseriesEntry(
                time = "2026-06-17T%02d:00:00Z".format(hour),
                data = MetEntryData(
                    instant = MetInstant(details = MetInstantDetails(airTemperature = 10.0)),
                    next6Hours = MetPeriod(
                        summary = MetSummary(symbolCode = "cloudy"),
                        details = MetPeriodDetails(
                            airTemperatureMax = if (hour == 12L) 14.0 else 11.0,
                            airTemperatureMin = if (hour == 0L) 6.0 else 9.0,
                        ),
                    ),
                ),
            )
        }
        val data = adapter.mapToWeatherData(
            MetNorwayResponse(type = "Feature", properties = MetProperties(timeseries = entries)),
            60.39, 5.32, null, ZoneId.of("UTC"),
        )
        val day = data.daily.first { it.date == LocalDate.of(2026, 6, 17) }
        assertEquals(14.0, day.temperatureHigh, 0.001)
        assertEquals(6.0, day.temperatureLow, 0.001)
    }

    @Test
    fun `instant samples still win the extrema when they exceed the six-hour bounds`() {
        val adapter = MetNorwayForecastAdapter(mockk(), MetNorwayHttpCache())
        val entries = listOf(
            MetTimeseriesEntry(
                time = "2026-06-17T00:00:00Z",
                data = MetEntryData(
                    instant = MetInstant(details = MetInstantDetails(airTemperature = 4.0)),
                    next6Hours = MetPeriod(
                        summary = MetSummary(symbolCode = "cloudy"),
                        details = MetPeriodDetails(airTemperatureMax = 12.0, airTemperatureMin = 5.0),
                    ),
                ),
            ),
            MetTimeseriesEntry(
                time = "2026-06-17T12:00:00Z",
                data = MetEntryData(
                    instant = MetInstant(details = MetInstantDetails(airTemperature = 16.0)),
                ),
            ),
        )
        val data = adapter.mapToWeatherData(
            MetNorwayResponse(type = "Feature", properties = MetProperties(timeseries = entries)),
            60.39, 5.32, null, ZoneId.of("UTC"),
        )
        val day = data.daily.first { it.date == LocalDate.of(2026, 6, 17) }
        assertEquals(16.0, day.temperatureHigh, 0.001)
        assertEquals(4.0, day.temperatureLow, 0.001)
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

    @Test
    fun `304 Not Modified returns cached response`() = runTest {
        val api = mockk<MetNorwayApi>()
        val cache = MetNorwayHttpCache()
        val adapter = MetNorwayForecastAdapter(api, cache)

        val entries = listOf(buildEntry(0, 15.0, symbol = "clearsky_day"))
        val metResponse = MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = entries),
        )

        cache.put(60.39, 5.32, MetNorwayHttpCache.CacheEntry(
            lastModified = "Sun, 14 Jun 2026 12:00:00 GMT",
            expires = null,
            response = metResponse,
        ))

        val rawResponse = okhttp3.Response.Builder()
            .code(304)
            .message("Not Modified")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://api.met.no/test").build())
            .build()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.error(
            "".toByteArray().toResponseBody(null),
            rawResponse,
        )

        val data = adapter.getWeather(60.39, 5.32, "Oslo").getOrThrow()
        assertEquals("Oslo", data.location.name)
        assertEquals(15.0, data.current.temperature, 0.01)
    }

    @Test
    fun `fresh cache skips network entirely`() = runTest {
        val api = mockk<MetNorwayApi>()
        val cache = MetNorwayHttpCache()
        val adapter = MetNorwayForecastAdapter(api, cache)

        val entries = listOf(buildEntry(0, 20.0, symbol = "fair_day"))
        val metResponse = MetNorwayResponse(
            type = "Feature",
            properties = MetProperties(timeseries = entries),
        )

        cache.put(60.39, 5.32, MetNorwayHttpCache.CacheEntry(
            lastModified = "Sun, 14 Jun 2026 12:00:00 GMT",
            expires = java.time.Instant.now().plusSeconds(3600),
            response = metResponse,
        ))

        val data = adapter.getWeather(60.39, 5.32, "Bergen").getOrThrow()
        assertEquals(20.0, data.current.temperature, 0.01)
        io.mockk.verify(exactly = 0) { api.hashCode() }
    }

    @Test
    fun `http cache coordinate key rounds to two decimals`() {
        val cache = MetNorwayHttpCache()
        assertEquals("60.39,5.32", cache.coordKey(60.3912, 5.3178))
        assertEquals("60.39,5.32", cache.coordKey(60.3901, 5.3249))
    }

    @Test
    fun `parseHttpDate parses RFC 1123 dates`() {
        val result = MetNorwayHttpCache.parseHttpDate("Sun, 14 Jun 2026 12:00:00 GMT")
        assertNotNull(result)
        val missing = MetNorwayHttpCache.parseHttpDate(null)
        assertNull(missing)
        val invalid = MetNorwayHttpCache.parseHttpDate("not-a-date")
        assertNull(invalid)
    }
}
