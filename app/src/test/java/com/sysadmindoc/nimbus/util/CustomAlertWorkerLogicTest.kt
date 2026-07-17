package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.AirQualityRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

/**
 * Pins the AQI fetch-failure retry contract: a user whose only enabled rules
 * are AQI thresholds must get a bounded [ListenableWorker.Result.retry]
 * (matching the weather-failure retry) instead of a silent no-op when the AQI
 * fetch fails. The per-(rule,threshold,date) dedupe store makes retries safe
 * for any weather rules that already fired this run.
 */
class CustomAlertWorkerLogicTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var airQualityRepository: AirQualityRepository
    private lateinit var prefs: UserPreferences

    private val aqiRule = CustomAlertRule(
        id = "rule-aqi",
        metric = CustomAlertMetric.AQI_NOW,
        operator = CustomAlertOperator.GREATER_THAN,
        thresholdCanonical = 100.0,
        enabled = true,
    )

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        params = mockk(relaxed = true)
        weatherRepository = mockk()
        airQualityRepository = mockk()
        prefs = mockk(relaxed = true)

        every { prefs.customAlertRules } returns flowOf(listOf(aqiRule))
        every { prefs.settings } returns flowOf(NimbusSettings())
        every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery {
            weatherRepository.getWeather(any(), any(), any(), any(), any(), any())
        } returns Result.success(weatherData())
    }

    private fun worker() = CustomAlertWorker(context, params, weatherRepository, airQualityRepository, prefs)

    @Test
    fun `AQI fetch failure with AQI rules enabled returns retry`() = runTest {
        coEvery { airQualityRepository.getAirQuality(any(), any()) } returns
            Result.failure(IOException("aqi outage"))

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun `AQI retry is bounded by the max attempt count`() = runTest {
        every { params.runAttemptCount } returns 3
        coEvery { airQualityRepository.getAirQuality(any(), any()) } returns
            Result.failure(IOException("aqi outage"))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test
    fun `successful AQI fetch below threshold succeeds without retry`() = runTest {
        val airQuality = mockk<AirQualityData>(relaxed = true) {
            every { usAqi } returns 40
        }
        coEvery { airQualityRepository.getAirQuality(any(), any()) } returns
            Result.success(airQuality)

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        coVerify(exactly = 1) { airQualityRepository.getAirQuality(any(), any()) }
    }

    @Test
    fun `AQI is not fetched when no enabled AQI rules exist`() = runTest {
        every { prefs.customAlertRules } returns flowOf(listOf(aqiRule.copy(enabled = false)))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        coVerify(exactly = 0) { airQualityRepository.getAirQuality(any(), any()) }
    }

    private fun weatherData(): WeatherData = WeatherData(
        location = LocationInfo(name = "Denver", latitude = 39.7, longitude = -104.9),
        current = CurrentConditions(
            temperature = 20.0,
            feelsLike = 19.0,
            humidity = 40,
            weatherCode = WeatherCode.CLEAR_SKY,
            observationTime = LocalDateTime.of(2026, 7, 15, 12, 0),
            isDay = true,
            windSpeed = 8.0,
            windDirection = 180,
            windGusts = null,
            pressure = 1013.0,
            uvIndex = 4.0,
            visibility = 10_000.0,
            dewPoint = 8.0,
            cloudCover = 5,
            precipitation = 0.0,
            dailyHigh = 25.0,
            dailyLow = 10.0,
            sunrise = null,
            sunset = null,
        ),
        hourly = emptyList(),
        daily = emptyList(),
        lastUpdated = LocalDateTime.of(2026, 7, 15, 12, 0),
        sourceProvider = "Open-Meteo",
    )
}
