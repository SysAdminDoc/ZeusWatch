package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ProviderAgreementAnalyzerTest {
    private val referenceTime = LocalDateTime.parse("2026-06-12T12:00:00")

    @Test
    fun analyzeReturnsStrongAgreementWhenTemperatureAndPrecipitationSpreadsAreTight() {
        val agreement = ProviderAgreementAnalyzer.analyze(
            forecasts = listOf(
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherData(temperatureC = 20.0, precipitationTotalMm = 1.0),
                ),
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.MET_NORWAY,
                    weatherData(temperatureC = 21.0, precipitationTotalMm = 2.5),
                ),
            ),
            referenceTime = referenceTime,
        )

        requireNotNull(agreement)
        assertEquals(ProviderAgreementLevel.STRONG, agreement.agreement)
        assertEquals(1.0, agreement.temperatureSpreadC, 0.001)
        assertEquals(1.5, agreement.precipitationSpreadMm, 0.001)
        assertEquals(2, agreement.providers.size)
    }

    @Test
    fun analyzeReturnsModerateAgreementForMaterialButBoundedSpread() {
        val agreement = ProviderAgreementAnalyzer.analyze(
            forecasts = listOf(
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherData(temperatureC = 18.0, precipitationTotalMm = 1.0),
                ),
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.MET_NORWAY,
                    weatherData(temperatureC = 20.5, precipitationTotalMm = 6.0),
                ),
            ),
            referenceTime = referenceTime,
        )

        requireNotNull(agreement)
        assertEquals(ProviderAgreementLevel.MODERATE, agreement.agreement)
    }

    @Test
    fun analyzeReturnsDivergentWhenAnySpreadIsLarge() {
        val agreement = ProviderAgreementAnalyzer.analyze(
            forecasts = listOf(
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherData(temperatureC = 16.0, precipitationTotalMm = 0.5),
                ),
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.MET_NORWAY,
                    weatherData(temperatureC = 20.0, precipitationTotalMm = 8.0),
                ),
            ),
            referenceTime = referenceTime,
        )

        requireNotNull(agreement)
        assertEquals(ProviderAgreementLevel.DIVERGENT, agreement.agreement)
    }

    @Test
    fun analyzeReturnsNullUntilAtLeastTwoProvidersAreAvailable() {
        val agreement = ProviderAgreementAnalyzer.analyze(
            forecasts = listOf(
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherData(temperatureC = 20.0, precipitationTotalMm = 1.0),
                ),
            ),
            referenceTime = referenceTime,
        )

        assertNull(agreement)
    }

    @Test
    fun analyzeExcludesProvidersWithoutARealHourlyWindowInsteadOfComparingInstantSamples() {
        // HKO-style provider: current conditions only, no hourly series. Falling
        // back to the instantaneous sample used to make the card report
        // divergence against other providers' 24h aggregates.
        val agreement = ProviderAgreementAnalyzer.analyze(
            forecasts = listOf(
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.HKO,
                    weatherData(temperatureC = 30.0, precipitationTotalMm = 0.0).copy(hourly = emptyList()),
                ),
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherData(temperatureC = 20.0, precipitationTotalMm = 1.0),
                ),
            ),
            referenceTime = referenceTime,
        )

        assertNull(agreement)
    }

    @Test
    fun analyzeExcludesProvidersWithFewerThanTwelveOverlappingHours() {
        val sparse = weatherData(temperatureC = 25.0, precipitationTotalMm = 1.0)
        val agreement = ProviderAgreementAnalyzer.analyze(
            forecasts = listOf(
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.HKO,
                    sparse.copy(hourly = sparse.hourly.take(5)),
                ),
                ProviderWeatherSnapshot(
                    WeatherSourceProvider.OPEN_METEO,
                    weatherData(temperatureC = 20.0, precipitationTotalMm = 1.0),
                ),
            ),
            referenceTime = referenceTime,
        )

        assertNull(agreement)
    }

    private fun weatherData(
        temperatureC: Double,
        precipitationTotalMm: Double,
    ): WeatherData {
        val hourlyPrecipitation = precipitationTotalMm / 24.0
        val hourly = (0 until 24).map { offset ->
            HourlyConditions(
                time = referenceTime.plusHours(offset.toLong()),
                temperature = temperatureC,
                feelsLike = temperatureC,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = offset in 6..18,
                precipitationProbability = 0,
                precipitation = hourlyPrecipitation,
                windSpeed = 10.0,
                windDirection = 180,
                humidity = 55,
                uvIndex = 2.0,
                cloudCover = 10,
                visibility = 15_000.0,
            )
        }
        return WeatherData(
            location = LocationInfo(
                name = "Test City",
                latitude = 40.0,
                longitude = -74.0,
                timeZone = "UTC",
            ),
            current = CurrentConditions(
                temperature = temperatureC,
                feelsLike = temperatureC,
                humidity = 55,
                weatherCode = WeatherCode.CLEAR_SKY,
                observationTime = referenceTime,
                isDay = true,
                windSpeed = 10.0,
                windDirection = 180,
                windGusts = null,
                pressure = 1013.0,
                uvIndex = 2.0,
                visibility = 15_000.0,
                dewPoint = 10.0,
                cloudCover = 10,
                precipitation = precipitationTotalMm,
                dailyHigh = temperatureC + 2.0,
                dailyLow = temperatureC - 2.0,
                sunrise = "06:00",
                sunset = "18:00",
            ),
            hourly = hourly,
            daily = listOf(
                DailyConditions(
                    date = LocalDate.parse("2026-06-12"),
                    weatherCode = WeatherCode.CLEAR_SKY,
                    temperatureHigh = temperatureC + 2.0,
                    temperatureLow = temperatureC - 2.0,
                    precipitationProbability = 0,
                    precipitationSum = precipitationTotalMm,
                    sunrise = "06:00",
                    sunset = "18:00",
                    uvIndexMax = 4.0,
                    windSpeedMax = 16.0,
                    windDirectionDominant = 180,
                ),
            ),
        )
    }
}
