package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.ArchiveDaily
import com.sysadmindoc.nimbus.data.api.ArchiveResponse
import com.sysadmindoc.nimbus.data.api.OpenMeteoArchiveApi
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeTravelRepositoryTest {

    private val today = LocalDate.of(2026, 7, 12)

    @Test
    fun `classify routes past dates to archive`() {
        assertEquals(TimeTravelSource.ARCHIVE, TimeTravelRange.classify(today.minusDays(1), today))
        assertEquals(TimeTravelSource.ARCHIVE, TimeTravelRange.classify(LocalDate.of(1995, 3, 4), today))
    }

    @Test
    fun `classify routes today and near-future to forecast`() {
        assertEquals(TimeTravelSource.FORECAST, TimeTravelRange.classify(today, today))
        assertEquals(TimeTravelSource.FORECAST, TimeTravelRange.classify(today.plusDays(15), today))
    }

    @Test
    fun `classify rejects dates outside the archive and forecast horizon`() {
        assertEquals(TimeTravelSource.OUT_OF_RANGE, TimeTravelRange.classify(today.plusDays(16), today))
        assertEquals(TimeTravelSource.OUT_OF_RANGE, TimeTravelRange.classify(LocalDate.of(1939, 12, 31), today))
    }

    @Test
    fun `getDay reads a future date from the loaded forecast without hitting archive`() = runTest {
        val api = mockk<OpenMeteoArchiveApi>() // never called; strict mock would fail if it were
        val repo = TimeTravelRepository(api)
        val target = today.plusDays(3)
        val forecast = listOf(dailyConditions(target, WeatherCode.CLEAR_SKY, high = 30.0, low = 18.0, precip = 0.0))

        val day = repo.getDay(52.0, 13.0, target, forecast, today)

        assertEquals(target, day?.date)
        assertEquals(WeatherCode.CLEAR_SKY, day?.weatherCode)
        assertEquals(30.0, day?.highC)
        assertFalse(day!!.isHistorical)
    }

    @Test
    fun `getDay returns null when a forecast date is missing from the window`() = runTest {
        val repo = TimeTravelRepository(mockk())
        val day = repo.getDay(52.0, 13.0, today.plusDays(2), emptyList(), today)
        assertNull(day)
    }

    @Test
    fun `getDay maps an archive observation for a past date`() = runTest {
        val api = mockk<OpenMeteoArchiveApi>()
        val date = LocalDate.of(2000, 1, 1)
        coEvery { api.getArchive(any(), any(), "2000-01-01", "2000-01-01") } returns ArchiveResponse(
            daily = ArchiveDaily(
                time = listOf("2000-01-01"),
                temperature_2m_max = listOf(5.0),
                temperature_2m_min = listOf(-2.0),
                precipitation_sum = listOf(3.4),
                weather_code = listOf(61),
            ),
        )
        val repo = TimeTravelRepository(api)

        val day = repo.getDay(52.0, 13.0, date, emptyList(), today)

        assertEquals(date, day?.date)
        assertEquals(5.0, day?.highC)
        assertEquals(-2.0, day?.lowC)
        assertEquals(3.4, day?.precipMm)
        assertTrue(day!!.isHistorical)
    }

    @Test
    fun `getDay returns null for out-of-range dates`() = runTest {
        val repo = TimeTravelRepository(mockk())
        assertNull(repo.getDay(52.0, 13.0, today.plusDays(40), emptyList(), today))
    }

    @Test
    fun `getDay resolves today in the location zone when no explicit today is given`() = runTest {
        // UTC-12 and UTC+14 are 26 hours apart, so the UTC-12 local date is
        // always strictly before the Kiritimati local date at any instant.
        val laggingZone = ZoneId.of("Etc/GMT+12")
        val leadingZone = ZoneId.of("Pacific/Kiritimati")
        val target = LocalDate.now(laggingZone)
        val forecast = listOf(dailyConditions(target, WeatherCode.CLEAR_SKY, high = 28.0, low = 20.0, precip = 0.0))

        // In the location's own (lagging) zone the date is "today", so the
        // loaded forecast answers it — a strict mock fails on any archive call.
        val forecastRepo = TimeTravelRepository(mockk())
        val day = forecastRepo.getDay(-13.9, -171.7, target, forecast, zoneId = laggingZone)
        assertEquals(target, day?.date)
        assertFalse(day!!.isHistorical)

        // Resolved against the leading zone the same date is already in the
        // past and must route to the archive instead.
        val iso = target.toString()
        val api = mockk<OpenMeteoArchiveApi>()
        coEvery { api.getArchive(any(), any(), iso, iso) } returns ArchiveResponse(
            daily = ArchiveDaily(
                time = listOf(iso),
                temperature_2m_max = listOf(10.0),
                temperature_2m_min = listOf(2.0),
                precipitation_sum = listOf(0.0),
                weather_code = listOf(1),
            ),
        )
        val archiveRepo = TimeTravelRepository(api)
        val archived = archiveRepo.getDay(1.87, -157.4, target, forecast, zoneId = leadingZone)
        assertTrue(archived!!.isHistorical)
    }

    private fun dailyConditions(
        date: LocalDate,
        code: WeatherCode,
        high: Double,
        low: Double,
        precip: Double?,
    ) = DailyConditions(
        date = date,
        weatherCode = code,
        temperatureHigh = high,
        temperatureLow = low,
        precipitationProbability = 0,
        precipitationSum = precip,
        sunrise = null,
        sunset = null,
        uvIndexMax = null,
        windSpeedMax = null,
        windDirectionDominant = null,
    )
}
