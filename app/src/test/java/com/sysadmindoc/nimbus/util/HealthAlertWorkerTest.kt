package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
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
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthAlertWorkerTest {

    @Test
    fun `doWork uses configured weather source path`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val params = mockk<WorkerParameters>(relaxed = true)
        val weatherRepository = mockk<WeatherRepository>()
        val prefs = mockk<UserPreferences>()

        every { prefs.settings } returns flowOf(
            NimbusSettings(
                healthAlertsEnabled = true,
                migraineAlerts = true,
            )
        )
        every { prefs.lastLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery {
            weatherRepository.getWeather(39.7, -104.9, "Denver")
        } returns Result.success(
            WeatherData(
                location = LocationInfo("Denver", latitude = 39.7, longitude = -104.9),
                current = CurrentConditions(
                    temperature = 20.0,
                    feelsLike = 20.0,
                    humidity = 50,
                    weatherCode = WeatherCode.CLEAR_SKY,
                    isDay = true,
                    windSpeed = 10.0,
                    windDirection = 180,
                    windGusts = null,
                    pressure = 1015.0,
                    uvIndex = 3.0,
                    visibility = 16.0,
                    dewPoint = 10.0,
                    cloudCover = 10,
                    precipitation = 0.0,
                    dailyHigh = 24.0,
                    dailyLow = 12.0,
                    sunrise = "2026-04-15T06:30:00",
                    sunset = "2026-04-15T19:30:00",
                ),
                hourly = emptyList(),
                daily = emptyList(),
            )
        )

        val worker = HealthAlertWorker(context, params, weatherRepository, prefs)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { weatherRepository.getWeather(39.7, -104.9, "Denver") }
        coVerify(exactly = 0) { weatherRepository.getWeatherDirect(any(), any(), any()) }
    }
}
