package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.repository.DeliveryFailureReason
import com.sysadmindoc.nimbus.data.repository.DeliveryHealthRepository
import com.sysadmindoc.nimbus.data.repository.DeliverySurface
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What each worker records, asserted against a real store.
 *
 * The workers took a relaxed mock for the delivery repository and nothing
 * checked a single recording decision: four classifications could be replaced
 * with a plain recordSuccess and the whole suite stayed green. These use the
 * real repository so the assertions are about the state a user would see in
 * the diagnostics panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeliveryRecordingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var deliveryHealth: DeliveryHealthRepository
    private lateinit var prefs: UserPreferences
    private lateinit var weatherRepository: WeatherRepository
    private val params: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setUp() = runTest {
        deliveryHealth = DeliveryHealthRepository(context)
        deliveryHealth.clear()
        prefs = mockk(relaxed = true)
        weatherRepository = mockk(relaxed = true)
    }

    private suspend fun entryFor(surface: DeliverySurface) =
        deliveryHealth.current().entries.firstOrNull { it.surface == surface }

    @Test
    fun `the daily briefing records no location when there is none`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(dailyBriefingEnabled = true))
        every { prefs.backgroundAlertLocation } returns flowOf(null)
        every { prefs.lastLocation } returns flowOf(null)

        DailyBriefingWorker(context, params, weatherRepository, prefs, deliveryHealth).doWork()

        assertEquals(
            DeliveryFailureReason.NO_LOCATION,
            entryFor(DeliverySurface.DAILY_BRIEFING)?.lastFailureReason,
        )
    }

    @Test
    fun `the daily briefing records an unavailable forecast rather than success`() = runTest {
        every { prefs.settings } returns flowOf(NimbusSettings(dailyBriefingEnabled = true))
        every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        every { prefs.lastLocation } returns flowOf(null)
        coEvery { weatherRepository.getWeather(any(), any(), any()) } returns
            Result.failure(java.io.IOException("down"))

        DailyBriefingWorker(context, params, weatherRepository, prefs, deliveryHealth).doWork()

        val entry = entryFor(DeliverySurface.DAILY_BRIEFING)
        assertEquals(DeliveryFailureReason.NO_NETWORK, entry?.lastFailureReason)
        assertNull("a failed run must not report a success", entry?.lastSuccessEpochMs)
    }

    @Test
    fun `turning the daily briefing off forgets the surface`() = runTest {
        deliveryHealth.recordFailure(
            DeliverySurface.DAILY_BRIEFING,
            DeliveryFailureReason.NO_NETWORK,
            1L,
        )
        every { prefs.settings } returns flowOf(NimbusSettings(dailyBriefingEnabled = false))

        DailyBriefingWorker(context, params, weatherRepository, prefs, deliveryHealth).doWork()

        assertNull(
            "a disabled surface must stop reporting a stale failure",
            entryFor(DeliverySurface.DAILY_BRIEFING),
        )
    }

    @Test
    fun `a surface that has never run has no entry at all`() = runTest {
        // Which is why the panel has to fill in a row for every enabled
        // surface rather than rendering only what the store holds.
        assertNull(entryFor(DeliverySurface.WIDGETS))
    }

    @Test
    fun `recording an attempt without an outcome leaves no success or failure`() = runTest {
        // The state a run that crashed leaves behind: tried, never finished.
        deliveryHealth.recordAttempt(DeliverySurface.WIDGETS, 1_000L)

        val entry = entryFor(DeliverySurface.WIDGETS)
        assertEquals(1_000L, entry?.lastAttemptEpochMs)
        assertNull(entry?.lastSuccessEpochMs)
        assertNull(entry?.lastFailureReason)
    }
}
