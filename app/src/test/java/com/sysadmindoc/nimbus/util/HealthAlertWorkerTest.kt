package com.sysadmindoc.nimbus.util

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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
        every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
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

    @Test
    fun `same-day severity escalation re-notifies but downgrade stays suppressed`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val params = mockk<WorkerParameters>(relaxed = true)
        val weatherRepository = mockk<WeatherRepository>()
        val prefs = mockk<UserPreferences>()
        wireMapBackedHealthStore(context)

        mockkObject(AlertNotificationHelper)
        try {
            every {
                AlertNotificationHelper.showHealthNotification(any(), any(), any(), any(), any(), any())
            } returns true
            every { prefs.settings } returns flowOf(
                NimbusSettings(
                    healthAlertsEnabled = true,
                    migraineAlerts = true,
                )
            )
            every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))

            val worker = HealthAlertWorker(context, params, weatherRepository, prefs)

            // Run 1: ADVISORY-range pressure delta (4 hPa / 3h) → notifies.
            coEvery { weatherRepository.getWeather(39.7, -104.9, "Denver") } returns
                Result.success(weatherWithPressures(ADVISORY_PRESSURES))
            worker.doWork()
            verify(exactly = 1) {
                AlertNotificationHelper.showHealthNotification(
                    any(), any(), any(), any(), any(), HealthSeverity.ADVISORY,
                )
            }

            // Run 2, same forecast day: escalation to WARNING (8 hPa / 3h)
            // must NOT be suppressed by the morning ADVISORY.
            coEvery { weatherRepository.getWeather(39.7, -104.9, "Denver") } returns
                Result.success(weatherWithPressures(WARNING_PRESSURES))
            worker.doWork()
            verify(exactly = 1) {
                AlertNotificationHelper.showHealthNotification(
                    any(), any(), any(), any(), any(), HealthSeverity.WARNING,
                )
            }

            // Run 3, same day: downgrade back to ADVISORY → stays suppressed
            // (still only the run-1 ADVISORY call).
            coEvery { weatherRepository.getWeather(39.7, -104.9, "Denver") } returns
                Result.success(weatherWithPressures(ADVISORY_PRESSURES))
            worker.doWork()
            verify(exactly = 1) {
                AlertNotificationHelper.showHealthNotification(
                    any(), any(), any(), any(), any(), HealthSeverity.ADVISORY,
                )
            }
        } finally {
            unmockkObject(AlertNotificationHelper)
        }
    }

    /**
     * Wires a map-backed [SharedPreferences] fake into the relaxed context so
     * [HealthAlertWorker]'s dedupe store persists across `doWork()` calls.
     */
    private fun wireMapBackedHealthStore(context: Context): MutableMap<String, Boolean> {
        val backing = mutableMapOf<String, Boolean>()
        val sp = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences("nimbus_health_alerts", any()) } returns sp
        every { sp.getBoolean(any(), any()) } answers { backing[firstArg<String>()] ?: secondArg() }
        every { sp.all } answers { backing.toMap() }
        every { sp.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            backing[firstArg<String>()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            backing.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } just Runs
        return backing
    }

    /** 12 hourly buckets with the given surface pressures; everything else stable. */
    private fun weatherWithPressures(pressures: List<Double>): WeatherData {
        // Pinned to a fixed date so every run resolves the same forecast-local
        // "today" for the dedupe key, independent of when the test executes.
        val baseTime = LocalDateTime.of(2026, 4, 15, 8, 0)
        val hourly = pressures.mapIndexed { i, p ->
            HourlyConditions(
                time = baseTime.plusHours(i.toLong()),
                temperature = 20.0,
                feelsLike = 20.0,
                weatherCode = WeatherCode.CLEAR_SKY,
                isDay = true,
                precipitationProbability = 0,
                precipitation = 0.0,
                windSpeed = 5.0,
                windDirection = 180,
                humidity = 50,
                uvIndex = 3.0,
                cloudCover = 50,
                visibility = 10000.0,
                surfacePressure = p,
            )
        }
        return WeatherData(
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
            hourly = hourly,
            daily = emptyList(),
        )
    }

    private companion object {
        /** 4 hPa drop over 3h → ADVISORY (default threshold 5.0 × 0.6 = 3.0). */
        val ADVISORY_PRESSURES = listOf(1015.0, 1014.0, 1012.5, 1011.0) + List(8) { 1011.0 }

        /** 8 hPa drop over 3h → WARNING (≥ default threshold 5.0). */
        val WARNING_PRESSURES = listOf(1015.0, 1013.0, 1010.0, 1007.0) + List(8) { 1007.0 }
    }
}
