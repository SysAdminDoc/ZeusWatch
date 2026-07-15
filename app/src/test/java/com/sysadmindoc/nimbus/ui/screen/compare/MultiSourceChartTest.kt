package com.sysadmindoc.nimbus.ui.screen.compare

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MultiSourceChartTest {

    @Test
    fun `summary reports source names and maximum hourly spreads`() {
        val summary = buildCompareOverlaySummary(
            listOf(
                CompareOverlayForecast(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherFor(
                        provider = WeatherSourceProvider.OPEN_METEO,
                        temperatures = listOf(20.0, 21.0, 22.0),
                        precipitation = listOf(10, 20, 30),
                    ),
                ),
                CompareOverlayForecast(
                    WeatherSourceProvider.MET_NORWAY,
                    weatherFor(
                        provider = WeatherSourceProvider.MET_NORWAY,
                        temperatures = listOf(18.0, 24.0, 23.0),
                        precipitation = listOf(40, 10, 35),
                    ),
                ),
            )
        )

        assertNotNull(summary)
        assertEquals(
            listOf(WeatherSourceProvider.OPEN_METEO.displayName, WeatherSourceProvider.MET_NORWAY.displayName),
            summary!!.sourceNames,
        )
        assertEquals(3.0, summary.maxTemperatureSpreadCelsius, 0.001)
        assertEquals(30, summary.maxPrecipitationSpreadPoints)
    }

    @Test
    fun `chart series converts hourly temperatures to display units`() {
        val series = buildMultiSourceChartSeries(
            forecasts = listOf(
                CompareOverlayForecast(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherFor(
                        provider = WeatherSourceProvider.OPEN_METEO,
                        temperatures = listOf(0.0, 10.0),
                        precipitation = listOf(0, 20),
                    ),
                ),
            ),
            settings = NimbusSettings(tempUnit = TempUnit.FAHRENHEIT),
        )

        assertEquals(1, series.size)
        assertEquals(32.0, series.first().points[0].displayTemperature, 0.001)
        assertEquals(50.0, series.first().points[1].displayTemperature, 0.001)
    }

    @Test
    fun `time domain spans the union of all series ranges`() {
        val base = LocalDateTime.of(2026, 7, 5, 8, 0)
        val early = seriesAt(base, temperatures = listOf(20.0, 21.0, 22.0))
        val late = seriesAt(base.plusHours(2), temperatures = listOf(18.0, 19.0, 20.0, 21.0))

        val domain = overlayTimeDomain(listOf(early, late))

        assertNotNull(domain)
        assertEquals(base, domain!!.first)
        assertEquals(base.plusHours(5), domain.second)
    }

    @Test
    fun `time domain is null when no series has points`() {
        assertNull(overlayTimeDomain(emptyList()))
    }

    @Test
    fun `time fraction positions offset series by timestamp instead of index`() {
        val base = LocalDateTime.of(2026, 7, 5, 8, 0)
        val start = base
        val end = base.plusHours(4)

        // A series starting two hours late must begin at the 0.5 mark, not at x=0.
        assertEquals(0.5f, overlayTimeFraction(base.plusHours(2), start, end), 0.0001f)
        assertEquals(0f, overlayTimeFraction(base, start, end), 0.0001f)
        assertEquals(1f, overlayTimeFraction(end, start, end), 0.0001f)
        // Out-of-domain timestamps clamp instead of drawing outside the chart.
        assertEquals(0f, overlayTimeFraction(base.minusHours(1), start, end), 0.0001f)
        assertEquals(1f, overlayTimeFraction(end.plusHours(3), start, end), 0.0001f)
    }

    @Test
    fun `precip bars align by timestamp and keep the max probability per hour`() {
        val base = LocalDateTime.of(2026, 7, 5, 8, 0)
        val one = seriesAt(base, temperatures = listOf(20.0, 21.0), precipitation = listOf(10, 60))
        val two = seriesAt(base.plusHours(1), temperatures = listOf(19.0, 18.0), precipitation = listOf(30, 45))

        val bars = buildOverlayPrecipBars(listOf(one, two))

        assertEquals(3, bars.size)
        assertEquals(base, bars[0].time)
        assertEquals(10, bars[0].probability)
        // Overlapping hour keeps the higher of 60 and 30.
        assertEquals(base.plusHours(1), bars[1].time)
        assertEquals(60, bars[1].probability)
        assertEquals(base.plusHours(2), bars[2].time)
        assertEquals(45, bars[2].probability)
    }

    @Test
    fun `line dash patterns differ per series and cycle with the palette`() {
        val patterns = (0 until 3).map { chartLineDashPatternDp(it) }

        assertNull(patterns[0])
        assertNotNull(patterns[1])
        assertNotNull(patterns[2])
        assertEquals(3, patterns.distinct().size)
        // Pattern assignment cycles alongside the three-color palette.
        assertEquals(patterns[0], chartLineDashPatternDp(3))
        assertEquals(patterns[1], chartLineDashPatternDp(4))
        assertEquals(patterns[2], chartLineDashPatternDp(5))
    }

    private fun seriesAt(
        start: LocalDateTime,
        temperatures: List<Double>,
        precipitation: List<Int> = List(temperatures.size) { 0 },
    ): MultiSourceChartSeries = MultiSourceChartSeries(
        label = "Series ${start.hour}",
        points = temperatures.mapIndexed { index, temp ->
            MultiSourceChartPoint(
                time = start.plusHours(index.toLong()),
                displayTemperature = temp,
                precipitationProbability = precipitation[index],
            )
        },
        currentTemperatureCelsius = temperatures.first(),
    )

    private fun weatherFor(
        provider: WeatherSourceProvider,
        temperatures: List<Double>,
        precipitation: List<Int>,
    ): WeatherData {
        return WeatherData(
            location = LocationInfo(
                name = "Denver",
                latitude = 39.7392,
                longitude = -104.9903,
            ),
            current = CurrentConditions(
                temperature = temperatures.first(),
                feelsLike = temperatures.first(),
                humidity = 45,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = true,
                windSpeed = 8.0,
                windDirection = 180,
                windGusts = null,
                pressure = 1012.0,
                uvIndex = 4.0,
                visibility = 10_000.0,
                dewPoint = null,
                cloudCover = 10,
                precipitation = 0.0,
                dailyHigh = temperatures.maxOrNull() ?: temperatures.first(),
                dailyLow = temperatures.minOrNull() ?: temperatures.first(),
                sunrise = null,
                sunset = null,
            ),
            hourly = temperatures.mapIndexed { index, temp ->
                HourlyConditions(
                    time = LocalDateTime.of(2026, 7, 5, 8, 0).plusHours(index.toLong()),
                    temperature = temp,
                    feelsLike = null,
                    weatherCode = WeatherCode.CLEAR_SKY,
                    isDay = true,
                    precipitationProbability = precipitation[index],
                    precipitation = null,
                    windSpeed = null,
                    windDirection = null,
                    humidity = null,
                    uvIndex = null,
                    cloudCover = null,
                    visibility = null,
                )
            },
            daily = emptyList(),
            sourceProvider = provider.displayName,
        )
    }
}
