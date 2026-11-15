package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.DeliveryHealthRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/**
 * The once-a-day check, run through the worker itself. The clock is set by
 * picking a fixed-offset zone where it reads the time a test needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DailyBriefingOncePerDayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: UserPreferences = mockk(relaxed = true)
    private val weatherRepository: WeatherRepository = mockk()
    private val networkPolicy: BackgroundNetworkPolicy = mockk()
    private val deliveryHealth: DeliveryHealthRepository = mockk(relaxed = true)

    private val originalZone: TimeZone = TimeZone.getDefault()

    private val weather = WeatherData(
        location = LocationInfo(name = "Denver", latitude = 39.7, longitude = -104.9),
        current = mockk(relaxed = true),
        hourly = emptyList(),
        daily = emptyList(),
    )

    @Before
    fun setUp() {
        // A scheduled run pins its next one through WorkManager.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
        every { networkPolicy.holdsBack(any(), any()) } returns false
        coEvery { weatherRepository.getWeather(any(), any(), any()) } returns Result.success(weather)
        mockkObject(WeatherSummaryEngine, WeatherNotificationHelper)
        every { WeatherSummaryEngine.generate(any(), any(), any(), any(), any(), any()) } returns "Sunny"
        every { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) } returns true
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
        unmockkObject(WeatherSummaryEngine, WeatherNotificationHelper)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `a briefing held past midnight goes out once`() = runTest {
        useBriefingTime(23 * 60 + 30)
        useClockAt(hour = 0, minute = 15)
        runScheduled()

        // 35 minutes west it's 23:40 the night before: still that night's
        // 23:30 briefing, already sent, and not worth a fetch.
        moveClock(minutes = -35)
        runScheduled()

        verify(exactly = 1) { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) }
        coVerify(exactly = 1) { weatherRepository.getWeather(any(), any(), any()) }
    }

    @Test
    fun `a briefing already sent isn't reported as held for Wi-Fi`() = runTest {
        useBriefingTime(7 * 60)
        useClockAt(hour = 8, minute = 0)
        runScheduled()

        // The job runs again, now on mobile data with Wi-Fi only on.
        every { networkPolicy.holdsBack(any(), any()) } returns true
        runScheduled()

        coVerify(exactly = 0) { deliveryHealth.recordSkip(any(), any(), any()) }
        coVerify(exactly = 2) { deliveryHealth.recordSuccess(DeliverySurface.DAILY_BRIEFING, any(), any()) }
    }

    @Test
    fun `after a trip west a run that comes early waits for the chosen time`() = runTest {
        useBriefingTime(7 * 60)
        // Pinned at home, where it's 07:00 and the run is on time. Nothing
        // has gone out, as when yesterday's was held for Wi-Fi.
        useClockAt(hour = 7, minute = 0)
        DailyBriefingStore(context).recordPinnedZone(ZonedDateTime.now().zone.id)

        // Three hours west it's 04:00, and the app hasn't started to move it.
        moveClock(minutes = -180)
        runScheduled()

        verify(exactly = 0) { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) }
        coVerify(exactly = 0) { weatherRepository.getWeather(any(), any(), any()) }
        val seven = ZonedDateTime.now().withHour(7).withMinute(0).truncatedTo(ChronoUnit.MINUTES)
        assertEquals(seven.toInstant().toEpochMilli(), briefingWorkInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a run held overnight still posts after a trip east`() = runTest {
        useBriefingTime(7 * 60)
        // Pinned at home, where it's 06:00: yesterday's run, held all night.
        useClockAt(hour = 6, minute = 0)
        DailyBriefingStore(context).recordPinnedZone(ZonedDateTime.now().zone.id)

        // Three hours east it's 09:00, past the chosen time on this clock.
        moveClock(minutes = 180)
        runScheduled()

        verify(exactly = 1) { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) }
    }

    @Test
    fun `Run now posts after a trip west too`() = runTest {
        useBriefingTime(7 * 60)
        useClockAt(hour = 7, minute = 0)
        DailyBriefingStore(context).recordPinnedZone(ZonedDateTime.now().zone.id)
        moveClock(minutes = -180)

        runNow()

        verify(exactly = 1) { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) }
    }

    @Test
    fun `Run now posts after the day's briefing went out`() = runTest {
        useBriefingTime(7 * 60)
        useClockAt(hour = 8, minute = 0)
        runScheduled()

        runNow()

        verify(exactly = 2) { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) }
    }

    @Test
    fun `Run now doesn't stand in for the scheduled briefing`() = runTest {
        useBriefingTime(7 * 60)
        useClockAt(hour = 8, minute = 0)
        runNow()

        // The briefing held past its 07:00 still goes out.
        runScheduled()

        verify(exactly = 2) { WeatherNotificationHelper.showDailyBriefing(any(), any(), any(), any()) }
    }

    private suspend fun runScheduled() {
        worker(Data.EMPTY).doWork()
    }

    private suspend fun runNow() {
        worker(BackgroundWork.userInitiatedInput()).doWork()
    }

    private fun briefingWorkInfo(): WorkInfo =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DailyBriefingWorker.UNIQUE_WORK_NAME)
            .get()
            .single()

    private fun worker(input: Data): DailyBriefingWorker {
        val params: WorkerParameters = mockk(relaxed = true)
        every { params.inputData } returns input
        return DailyBriefingWorker(context, params, weatherRepository, prefs, deliveryHealth, networkPolicy)
    }

    private fun useBriefingTime(minutes: Int) {
        every { prefs.settings } returns flowOf(
            NimbusSettings(dailyBriefingEnabled = true, dailyBriefingMinutes = minutes),
        )
    }

    /** A fixed offset where the clock now reads [hour]:[minute]. */
    private fun useClockAt(hour: Int, minute: Int) {
        val utc = ZonedDateTime.now(ZoneOffset.UTC)
        val offsetMinutes = Math.floorMod(hour * 60 + minute - (utc.hour * 60 + utc.minute) + 720, 1440) - 720
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)))
    }

    /** The same moment on a clock [minutes] ahead of the current zone, or behind. */
    private fun moveClock(minutes: Int) {
        val offsetSeconds = TimeZone.getDefault().rawOffset / 1000 + minutes * 60
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(offsetSeconds)))
    }
}
