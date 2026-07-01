package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandData
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ForecastUncertaintyExplainerTest {
    private val baseTime = LocalDateTime.of(2026, 1, 15, 9, 0)

    @Test
    fun summarizeHourly_classifiesAverageSpread() {
        val hourly = (0 until 3).map { hour(offset = it) }
        val bands = ConfidenceBandData(
            entries = listOf(
                band(0, lower = 9.0, upper = 10.0),
                band(1, lower = 10.0, upper = 11.0),
                band(2, lower = 11.0, upper = 12.0),
            ),
        )

        val summary = ForecastUncertaintyExplainer.summarizeHourly(hourly, bands)

        assertEquals(1.0, summary?.averageSpreadC ?: -1.0, 0.001)
        assertEquals(ForecastUncertaintyLevel.LOW, summary?.level)
        assertEquals(3, summary?.sampleCount)
    }

    @Test
    fun detailForHour_returnsP10P90RangeForSelectedHour() {
        val hour = hour(offset = 2)
        val bands = ConfidenceBandData(
            entries = listOf(
                band(1, lower = 10.0, upper = 13.0),
                band(2, lower = 11.0, upper = 17.0),
            ),
        )

        val detail = ForecastUncertaintyExplainer.detailForHour(hour, bands)

        assertEquals(11.0, detail?.lowerC ?: -1.0, 0.001)
        assertEquals(17.0, detail?.upperC ?: -1.0, 0.001)
        assertEquals(6.0, detail?.spreadC ?: -1.0, 0.001)
        assertEquals(ForecastUncertaintyLevel.HIGH, detail?.level)
        assertEquals(1, detail?.sampleCount)
    }

    @Test
    fun detailForDay_averagesHourlyRangesWithinDate() {
        val day = daily(baseTime.toLocalDate())
        val bands = ConfidenceBandData(
            entries = listOf(
                band(0, lower = 8.0, upper = 10.0),
                band(1, lower = 9.0, upper = 12.0),
                band(30, lower = 20.0, upper = 30.0),
            ),
        )

        val detail = ForecastUncertaintyExplainer.detailForDay(day, bands)

        assertEquals(8.5, detail?.lowerC ?: -1.0, 0.001)
        assertEquals(11.0, detail?.upperC ?: -1.0, 0.001)
        assertEquals(2.5, detail?.spreadC ?: -1.0, 0.001)
        assertEquals(ForecastUncertaintyLevel.MEDIUM, detail?.level)
        assertEquals(2, detail?.sampleCount)
    }

    @Test
    fun invalidConfidenceRange_isIgnored() {
        val hourly = listOf(hour(offset = 0), hour(offset = 1))
        val bands = ConfidenceBandData(
            entries = listOf(
                band(0, lower = 12.0, upper = 10.0),
                band(1, lower = 13.0, upper = 11.0),
            ),
        )

        assertNull(ForecastUncertaintyExplainer.summarizeHourly(hourly, bands))
        assertNull(ForecastUncertaintyExplainer.detailForHour(hourly.first(), bands))
    }

    @Test
    fun classifySpread_usesLowMediumHighThresholds() {
        assertEquals(ForecastUncertaintyLevel.LOW, ForecastUncertaintyExplainer.classifySpread(1.99))
        assertEquals(ForecastUncertaintyLevel.MEDIUM, ForecastUncertaintyExplainer.classifySpread(2.0))
        assertEquals(ForecastUncertaintyLevel.HIGH, ForecastUncertaintyExplainer.classifySpread(5.0))
    }

    private fun hour(offset: Int): HourlyConditions = HourlyConditions(
        time = baseTime.plusHours(offset.toLong()),
        temperature = 10.0 + offset,
        feelsLike = null,
        weatherCode = WeatherCode.PARTLY_CLOUDY,
        isDay = true,
        precipitationProbability = 0,
        precipitation = null,
        windSpeed = null,
        windDirection = null,
        humidity = null,
        uvIndex = null,
        cloudCover = null,
        visibility = null,
    )

    private fun daily(date: LocalDate): DailyConditions = DailyConditions(
        date = date,
        weatherCode = WeatherCode.PARTLY_CLOUDY,
        temperatureHigh = 16.0,
        temperatureLow = 8.0,
        precipitationProbability = 10,
        precipitationSum = null,
        sunrise = null,
        sunset = null,
        uvIndexMax = null,
        windSpeedMax = null,
        windDirectionDominant = null,
    )

    private fun band(
        offset: Int,
        lower: Double,
        upper: Double,
    ): ConfidenceBandEntry = ConfidenceBandEntry(
        time = baseTime.plusHours(offset.toLong()),
        temperatureMean = (lower + upper) / 2.0,
        temperatureLower = lower,
        temperatureUpper = upper,
    )
}
