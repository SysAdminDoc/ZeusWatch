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
