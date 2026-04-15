package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.BrightSkyApi
import com.sysadmindoc.nimbus.data.api.OpenWeatherMapApi
import com.sysadmindoc.nimbus.data.api.PirateWeatherApi
import com.sysadmindoc.nimbus.data.model.BrightSkyWeatherResponse
import com.sysadmindoc.nimbus.data.model.BsSource
import com.sysadmindoc.nimbus.data.model.BsWeatherEntry
import com.sysadmindoc.nimbus.data.model.OwmCurrent
import com.sysadmindoc.nimbus.data.model.OwmDaily
import com.sysadmindoc.nimbus.data.model.OwmDailyTemp
import com.sysadmindoc.nimbus.data.model.OwmHourly
import com.sysadmindoc.nimbus.data.model.OwmOneCallResponse
import com.sysadmindoc.nimbus.data.model.OwmWeatherDesc
import com.sysadmindoc.nimbus.data.model.PirateWeatherResponse
import com.sysadmindoc.nimbus.data.model.PwCurrently
import com.sysadmindoc.nimbus.data.model.PwDaily
import com.sysadmindoc.nimbus.data.model.PwDailyBlock
import com.sysadmindoc.nimbus.data.model.PwHourly
import com.sysadmindoc.nimbus.data.model.PwHourlyBlock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.TimeZone

class ForecastAdapterTimezoneTest {

    @Test
    fun `owm hourly mapping uses response timezone instead of device timezone`() = runTest {
        withDefaultTimeZone("America/New_York") {
            val api = mockk<OpenWeatherMapApi>()
            val prefs = mockk<UserPreferences>()
            val adapter = OwmForecastAdapter(api, prefs)
            val locationZone = ZoneId.of("Pacific/Pago_Pago")
            val currentInstant = Instant.now().truncatedTo(ChronoUnit.HOURS)

            every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "test-key"))
            coEvery { api.getOneCall(any(), any(), any(), any(), any()) } returns OwmOneCallResponse(
                lat = -14.2756,
                lon = -170.702,
                timezone = locationZone.id,
                current = OwmCurrent(
                    dt = currentInstant.epochSecond,
                    temp = 25.0,
                    feelsLike = 25.0,
                    weather = listOf(OwmWeatherDesc(id = 800, icon = "01d")),
                ),
                hourly = (-1L..2L).map { offset ->
                    OwmHourly(
                        dt = currentInstant.plus(offset, ChronoUnit.HOURS).epochSecond,
                        temp = 20.0 + offset,
                        weather = listOf(OwmWeatherDesc(id = 800, icon = "01d")),
                    )
                },
                daily = listOf(
                    OwmDaily(
                        dt = currentInstant.epochSecond,
                        temp = OwmDailyTemp(min = 21.0, max = 28.0),
                        weather = listOf(OwmWeatherDesc(id = 800, icon = "01d")),
                    )
                ),
            )

            val result = adapter.getWeather(-14.2756, -170.702, "Pago Pago").getOrThrow()

            assertEquals("Pago Pago", result.location.name)
            assertEquals(4, result.hourly.size)
            assertTrue(result.hourly.first().time <= result.hourly.last().time)
        }
    }

    @Test
    fun `pirate weather hourly mapping uses response timezone instead of device timezone`() = runTest {
        withDefaultTimeZone("America/New_York") {
            val api = mockk<PirateWeatherApi>()
            val prefs = mockk<UserPreferences>()
            val adapter = PirateWeatherForecastAdapter(api, prefs)
            val locationZone = ZoneId.of("Pacific/Pago_Pago")
            val currentInstant = Instant.now().truncatedTo(ChronoUnit.HOURS)

            every { prefs.settings } returns flowOf(NimbusSettings(pirateWeatherApiKey = "test-key"))
            coEvery { api.getForecast(any(), any(), any(), any(), any()) } returns PirateWeatherResponse(
                latitude = -14.2756,
                longitude = -170.702,
                timezone = locationZone.id,
                currently = PwCurrently(
                    time = currentInstant.epochSecond,
                    icon = "clear-day",
                    temperature = 24.0,
                    apparentTemperature = 24.0,
                    humidity = 0.55,
                    pressure = 1012.0,
                    windSpeed = 4.0,
                    cloudCover = 0.2,
                ),
                hourly = PwHourlyBlock(
                    data = (-1L..2L).map { offset ->
                        PwHourly(
                            time = currentInstant.plus(offset, ChronoUnit.HOURS).epochSecond,
                            icon = "clear-day",
                            temperature = 19.0 + offset,
                            apparentTemperature = 19.0 + offset,
                            humidity = 0.5,
                            cloudCover = 0.2,
                            precipProbability = 0.0,
                        )
                    }
                ),
                daily = PwDailyBlock(
                    data = listOf(
                        PwDaily(
                            time = currentInstant.epochSecond,
                            icon = "clear-day",
                            temperatureHigh = 27.0,
                            temperatureLow = 20.0,
                        )
                    )
                ),
            )

            val result = adapter.getWeather(-14.2756, -170.702, "Pago Pago").getOrThrow()

            assertEquals("Pago Pago", result.location.name)
            assertEquals(4, result.hourly.size)
            assertTrue(result.hourly.first().time <= result.hourly.last().time)
        }
    }

    @Test
    fun `bright sky hourly mapping anchors to current entry time instead of device timezone`() = runTest {
        withDefaultTimeZone("America/New_York") {
            val api = mockk<BrightSkyApi>()
            val adapter = BrightSkyForecastAdapter(api)
            val offset = ZoneOffset.ofHours(-11)
            val currentInstant = Instant.now().truncatedTo(ChronoUnit.HOURS)

            fun entryAt(offsetHours: Long, temperature: Double) = BsWeatherEntry(
                timestamp = OffsetDateTime.ofInstant(
                    currentInstant.plus(offsetHours, ChronoUnit.HOURS),
                    offset,
                ).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                temperature = temperature,
                humidity = 60.0,
                pressureMsl = 1011.0,
                windSpeed = 12.0,
                condition = "dry",
                icon = "clear-day",
            )

            coEvery { api.getWeather(any(), any(), any(), any(), any()) } returns BrightSkyWeatherResponse(
                weather = (-1L..2L).map { offsetHours -> entryAt(offsetHours, 18.0 + offsetHours) },
                sources = listOf(BsSource(id = 1, stationName = "Pago Pago")),
            )
            coEvery { api.getCurrentWeather(any(), any()) } returns BrightSkyWeatherResponse(
                weather = listOf(entryAt(0, 18.0)),
                sources = emptyList(),
            )

            val result = adapter.getWeather(-14.2756, -170.702, "Pago Pago").getOrThrow()

            assertEquals("Pago Pago", result.location.name)
            assertEquals(4, result.hourly.size)
            assertEquals(18.0, result.current.temperature, 0.01)
        }
    }

    private suspend fun <T> withDefaultTimeZone(zoneId: String, block: suspend () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
