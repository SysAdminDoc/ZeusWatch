package com.sysadmindoc.nimbus.util

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import com.sysadmindoc.nimbus.data.repository.DeliveryHealthRepository
import com.sysadmindoc.nimbus.data.repository.DeliverySkipReason
import com.sysadmindoc.nimbus.data.repository.DeliverySurface
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Pins the countdown-silence flag propagation: when the detected transition's
 * signature matches the stored one (a ~5-min countdown refresh of the SAME
 * event), the worker must ask the notification helper for a silent update;
 * a new signature must alert audibly.
 */
class NowcastAlertWorkerTest {
    // Relaxed: holdsBack is false, so nothing waits for Wi-Fi unless a test says so.
    private val networkPolicy: BackgroundNetworkPolicy = mockk(relaxed = true)

    // Relaxed: these tests are about what the worker delivers, not
    // about the diagnostics recording, which has its own tests.
    private val deliveryHealth: DeliveryHealthRepository = io.mockk.mockk(relaxed = true)


    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var prefs: UserPreferences
    private lateinit var dedupePrefs: SharedPreferences

    // Buckets anchored on the wall clock (the worker's reference-time source),
    // minute-aligned and minutes away from any boundary so the detected
    // transition is stable for the duration of a test run.
    private val base: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)
    private val series = listOf(
        MinutelyPrecipitation(time = base.minusMinutes(5), precipitation = 0.0),
        MinutelyPrecipitation(time = base.plusMinutes(10), precipitation = 1.0),
        MinutelyPrecipitation(time = base.plusMinutes(25), precipitation = 1.5),
    )

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        params = mockk(relaxed = true)
        weatherRepository = mockk()
        prefs = mockk(relaxed = true)
        dedupePrefs = mockk(relaxed = true)

        every { context.getSharedPreferences("nimbus_nowcast_alerts", any()) } returns dedupePrefs
        every { prefs.settings } returns flowOf(NimbusSettings(nowcastingAlerts = true))
        every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        coEvery { weatherRepository.getMinutelyPrecipitation(any(), any()) } returns Result.success(series)

        mockkObject(AlertNotificationHelper)
        // delivered=false keeps the store/recheck side effects (WorkManager) out
        // of the JVM test while still exercising the delivery call itself.
        every {
            AlertNotificationHelper.showNowcastNotification(any(), any(), any(), any(), any())
        } returns false
    }

    @After
    fun tearDown() {
        unmockkObject(AlertNotificationHelper)
    }

    private fun expectedSignature(): String =
        transitionSignature(detectNowcastTransition(series, nowcastReferenceTime(series))!!)

    @Test
    fun `new transition requests an audible notification`() = runTest {
        every { dedupePrefs.getString("last_signature", null) } returns null
        every { dedupePrefs.getLong("last_notified_at", 0L) } returns 0L

        val worker = NowcastAlertWorker(context, params, weatherRepository, prefs, deliveryHealth, networkPolicy)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) {
            AlertNotificationHelper.showNowcastNotification(any(), any(), any(), any(), eq(false))
        }
    }

    @Test
    fun `countdown refresh of the same transition requests a silent update`() = runTest {
        every { dedupePrefs.getString("last_signature", null) } returns expectedSignature()

        val worker = NowcastAlertWorker(context, params, weatherRepository, prefs, deliveryHealth, networkPolicy)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) {
            AlertNotificationHelper.showNowcastNotification(any(), any(), any(), any(), eq(true))
        }
    }

    @Test
    fun `a run held for Wi-Fi records a skip and fetches nothing`() = runTest {
        every { networkPolicy.holdsBack(any(), any()) } returns true

        val worker = NowcastAlertWorker(context, params, weatherRepository, prefs, deliveryHealth, networkPolicy)
        val result = worker.doWork()

        // Success: the next 15-minute run is the retry.
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            deliveryHealth.recordSkip(DeliverySurface.NOWCAST_ALERTS, DeliverySkipReason.METERED_NETWORK, any())
        }
        coVerify(exactly = 0) { deliveryHealth.recordAttempt(any(), any()) }
        coVerify(exactly = 0) { weatherRepository.getMinutelyPrecipitation(any(), any()) }
        verify(exactly = 0) {
            AlertNotificationHelper.showNowcastNotification(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `Run now on mobile data goes through with the Wi-Fi hold on`() = runTest {
        // The policy as it really answers on mobile data: hold a scheduled
        // run, never one the user started. A worker that dropped the mark
        // would be held here.
        every { networkPolicy.holdsBack(any(), false) } returns true
        every { networkPolicy.holdsBack(any(), true) } returns false
        every { params.inputData } returns BackgroundWork.userInitiatedInput()

        NowcastAlertWorker(context, params, weatherRepository, prefs, deliveryHealth, networkPolicy).doWork()

        coVerify(exactly = 1) { weatherRepository.getMinutelyPrecipitation(any(), any()) }
        coVerify(exactly = 0) { deliveryHealth.recordSkip(any(), any(), any()) }
    }
}
