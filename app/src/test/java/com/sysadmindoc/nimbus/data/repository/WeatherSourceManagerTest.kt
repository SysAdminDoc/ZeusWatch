package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherSourceManagerTest {

    private lateinit var prefs: UserPreferences
    private lateinit var openMeteoAdapter: OpenMeteoForecastAdapter
    private lateinit var openMeteoMinutelyAdapter: OpenMeteoMinutelyAdapter
    private lateinit var alertAdapter: AlertSourceManagerAdapter
    private lateinit var aqiAdapter: OpenMeteoAqiAdapter
    private lateinit var manager: WeatherSourceManager

    private val testWeatherData = WeatherData(
        location = LocationInfo(name = "Test City", latitude = 40.0, longitude = -74.0),
        current = CurrentConditions(
            temperature = 20.0, feelsLike = 18.0, humidity = 50,
            weatherCode = WeatherCode.CLEAR_SKY, isDay = true,
            windSpeed = 10.0, windDirection = 180, windGusts = null,
            pressure = 1013.0, uvIndex = 3.0, visibility = 15000.0,
            dewPoint = 10.0, cloudCover = 20, precipitation = 0.0,
            dailyHigh = 25.0, dailyLow = 15.0, sunrise = "06:00", sunset = "18:00",
        ),
        hourly = emptyList(),
        daily = emptyList(),
    )

    private val defaultSettings = NimbusSettings(
        sourceConfig = SourceConfig(
            forecast = WeatherSourceProvider.OPEN_METEO,
            forecastFallback = null,
            alerts = WeatherSourceProvider.NWS,
            alertsFallback = null,
            airQuality = WeatherSourceProvider.OPEN_METEO,
            minutely = WeatherSourceProvider.OPEN_METEO,
        )
    )

    @Before
    fun setup() {
        prefs = mockk()
        openMeteoAdapter = mockk()
        openMeteoMinutelyAdapter = mockk()
        alertAdapter = mockk()
        aqiAdapter = mockk()
        every { prefs.settings } returns flowOf(defaultSettings)
        manager = WeatherSourceManager(prefs, openMeteoAdapter, openMeteoMinutelyAdapter, alertAdapter, aqiAdapter)
    }

    // ── Forecast Tests ──

    @Test
    fun getWeatherReturnsSuccessFromPrimarySource() = runTest {
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(40.0, -74.0)
        assertTrue(result.isSuccess)
        assertEquals("Test City", result.getOrNull()?.location?.name)
    }

    @Test
    fun getWeatherFallsBackWhenPrimaryFailsWithSameProvider() = runTest {
        val settingsWithFallback = defaultSettings.copy(
            sourceConfig = SourceConfig(
                forecast = WeatherSourceProvider.OPEN_METEO,
                forecastFallback = WeatherSourceProvider.OPEN_METEO,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithFallback)
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.failure(Exception("Primary failed"))

        val result = manager.getWeather(40.0, -74.0)
        // Since fallback == primary, it returns the original failure
        assertTrue(result.isFailure)
    }

    @Test
    fun getWeatherReturnsFailureWhenNoFallbackConfigured() = runTest {
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.failure(Exception("Network error"))

        val result = manager.getWeather(40.0, -74.0)
        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    // ── Alert Tests ──

    @Test
    fun getAlertsReturnsSuccessFromNws() = runTest {
        val alerts = listOf(
            WeatherAlert(
                id = "1", event = "Tornado Warning", headline = "Tornado",
                description = "Take shelter", instruction = null,
                severity = AlertSeverity.EXTREME, urgency = AlertUrgency.IMMEDIATE,
                certainty = "Observed", senderName = "NWS",
                areaDescription = "Test County", effective = null, expires = null, response = null,
            )
        )
        coEvery { alertAdapter.getAlerts(any(), any()) } returns Result.success(alerts)

        val result = manager.getAlerts(40.0, -74.0)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun getAlertsReturnsFailureWhenSourceFailsNoFallback() = runTest {
        coEvery { alertAdapter.getAlerts(any(), any()) } returns Result.failure(Exception("NWS down"))

        val result = manager.getAlerts(40.0, -74.0)
        assertTrue(result.isFailure)
    }

    // ── Air Quality Tests ──

    @Test
    fun getAirQualityDelegatesToOpenMeteoAdapter() = runTest {
        val aqiData = mockk<AirQualityData>()
        coEvery { aqiAdapter.getAirQuality(any(), any()) } returns Result.success(aqiData)

        val result = manager.getAirQuality(40.0, -74.0)
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { aqiAdapter.getAirQuality(40.0, -74.0) }
    }

    @Test
    fun getAirQualityPropagatesFailure() = runTest {
        coEvery { aqiAdapter.getAirQuality(any(), any()) } returns Result.failure(Exception("AQI error"))

        val result = manager.getAirQuality(40.0, -74.0)
        assertTrue(result.isFailure)
    }

    // ── Minutely Tests ──

    @Test
    fun getMinutelyPrecipitationReturnsDataFromOpenMeteo() = runTest {
        val minutely = listOf(
            MinutelyPrecipitation(time = LocalDateTime.now(), precipitation = 0.5)
        )
        coEvery { openMeteoMinutelyAdapter.getMinutelyPrecipitation(any(), any()) } returns Result.success(minutely)

        val result = manager.getMinutelyPrecipitation(40.0, -74.0)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun getMinutelyPrecipitationPropagatesFailure() = runTest {
        coEvery { openMeteoMinutelyAdapter.getMinutelyPrecipitation(any(), any()) } returns Result.failure(Exception("Minutely error"))

        val result = manager.getMinutelyPrecipitation(40.0, -74.0)
        assertTrue(result.isFailure)
    }

    // ── Source Config Tests ──

    @Test
    fun forTypeFiltersOutUnimplementedProviders() {
        val forecastProviders = WeatherSourceProvider.forType(WeatherDataType.FORECAST)
        assertFalse("OWM should be hidden", forecastProviders.contains(WeatherSourceProvider.OPEN_WEATHER_MAP))
        assertFalse("Pirate Weather should be hidden", forecastProviders.contains(WeatherSourceProvider.PIRATE_WEATHER))
        assertFalse("Bright Sky should be hidden", forecastProviders.contains(WeatherSourceProvider.BRIGHT_SKY))
        assertTrue("Open-Meteo should be present", forecastProviders.contains(WeatherSourceProvider.OPEN_METEO))
    }

    @Test
    fun forTypeReturnsImplementedAlertProviders() {
        val alertProviders = WeatherSourceProvider.forType(WeatherDataType.ALERTS)
        assertTrue(alertProviders.contains(WeatherSourceProvider.NWS))
        assertTrue(alertProviders.contains(WeatherSourceProvider.ENVIRONMENT_CANADA))
        assertTrue(alertProviders.contains(WeatherSourceProvider.METEOALARM))
        assertTrue(alertProviders.contains(WeatherSourceProvider.JMA))
    }
}
