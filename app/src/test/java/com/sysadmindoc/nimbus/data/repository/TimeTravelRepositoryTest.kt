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
