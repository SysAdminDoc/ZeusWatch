package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.sysadmindoc.nimbus.data.repository.DeliveryHealthRepository
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SavedLocation
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * When the next briefing runs, asked of a real WorkManager.
 *
 * A periodic run is rescheduled a period after it finished, so a briefing
 * held by its battery or network constraint would arrive late every day
 * after unless each run pins the next one back on the chosen time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DailyBriefingScheduleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: UserPreferences = mockk(relaxed = true)
    private val weatherRepository: WeatherRepository = mockk(relaxed = true)
    private val networkPolicy: BackgroundNetworkPolicy = mockk()
    private val deliveryHealth: DeliveryHealthRepository = mockk(relaxed = true)

    private val originalZone: TimeZone = TimeZone.getDefault()

    // Hours away from now, so a pinned run can't be mistaken for one a day
    // after the last run or for the 15 minute backoff. Set once the test's
    // zone is in place.
    private lateinit var inThreeHours: ZonedDateTime
    private lateinit var inFiveHours: ZonedDateTime

    @Before
    fun setUp() {
        // A fixed offset where it's early morning: no clock change can land
        // inside a test, and every time below falls on today.
        TimeZone.setDefault(zoneWhereItIs(hour = 6))
        inThreeHours = ZonedDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES)
        inFiveHours = ZonedDateTime.now().plusHours(5).truncatedTo(ChronoUnit.MINUTES)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker =
                DailyBriefingWorker(appContext, workerParameters, weatherRepository, prefs, deliveryHealth, networkPolicy)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build(),
        )
        useBriefingTime(inThreeHours.minuteOfDay())
        every { prefs.backgroundAlertLocation } returns flowOf(SavedLocation(39.7, -104.9, "Denver"))
    }

    @After
    fun tearDown() {
        // First, so a failing close can't leave the zone behind for later tests.
        TimeZone.setDefault(originalZone)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `a held briefing that ran late keeps the next one on the chosen time`() {
        // Chosen for 3 hours ago: the battery or Doze held this run that long.
        val chosen = ZonedDateTime.now().minusHours(3).truncatedTo(ChronoUnit.MINUTES)
        useBriefingTime(chosen.minuteOfDay())
        every { networkPolicy.holdsBack(any(), false) } returns true
        // No pin of its own, so only the run's pin can put tomorrow's on time.
        DailyBriefingWorker.schedule(context, chosen.minuteOfDay())

        runFirstScheduledBriefing()

        verify(exactly = 1) { networkPolicy.holdsBack(any(), false) }
        assertEquals(WorkInfo.State.ENQUEUED, workInfo().state)
        // Unpinned, it would be a day after this run: 3 hours late for good.
        assertEquals(chosen.plusDays(1).millis(), workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a failed fetch retries on its backoff, through a cold start too`() {
        every { networkPolicy.holdsBack(any(), false) } returns false
        coEvery { weatherRepository.getWeather(any(), any(), any()) } returns Result.failure(IOException("down"))
        // Pinned, as every scheduled briefing is: the retry has to clear the
        // pin or it would run again at once.
        sync(inThreeHours.minuteOfDay())
        assertEquals(inThreeHours.millis(), workInfo().nextScheduleTimeMillis)

        val before = System.currentTimeMillis()
        runPinnedBriefing()
        val after = System.currentTimeMillis()

        coVerify(exactly = 1) { weatherRepository.getWeather(any(), any(), any()) }
        val backoff = TimeUnit.MINUTES.toMillis(15)
        val retryWindow = (before + backoff)..(after + backoff)
        assertTrue("next run at ${workInfo().nextScheduleTimeMillis}", workInfo().nextScheduleTimeMillis in retryWindow)

        // A pin here would put the retry off until tomorrow's briefing.
        sync(inThreeHours.minuteOfDay())
        assertTrue("next run at ${workInfo().nextScheduleTimeMillis}", workInfo().nextScheduleTimeMillis in retryWindow)
    }

    @Test
    fun `a cold start after a trip east leaves today's briefing on the old clock`() {
        sync(inThreeHours.minuteOfDay())
        val pinned = workInfo().nextScheduleTimeMillis
        assertEquals(inThreeHours.millis(), pinned)

        // Three hours east the chosen time is now, so a pin would make the
        // next one tomorrow's and today's would never come.
        TimeZone.setDefault(shifted(TimeZone.getDefault(), hours = 3))
        sync(inThreeHours.minuteOfDay())

        assertEquals(pinned, workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a cold start after a trip west moves today's briefing to the new clock`() {
        // Pinned three hours east, where it's about 09:00 and the briefing
        // is two hours off.
        val east = zoneWhereItIs(hour = 9)
        TimeZone.setDefault(east)
        val chosen = ZonedDateTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES)
        sync(chosen.minuteOfDay())
        assertEquals(chosen.millis(), workInfo().nextScheduleTimeMillis)

        // Back west it's about 06:00, and the old pin would ring at 08:00
        // here. It moves to the chosen time on this clock, still today, with
        // nothing sent yet (yesterday's could have been held for Wi-Fi).
        TimeZone.setDefault(shifted(east, hours = -3))
        sync(chosen.minuteOfDay())

        val here = chosen.withZoneSameLocal(ZonedDateTime.now().zone)
        assertEquals(here.millis(), workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a cold start just east of a late briefing leaves it to arrive after midnight`() {
        // 23:00 here, briefing at 23:20.
        val west = zoneWhereItIs(hour = 23, minute = 0)
        TimeZone.setDefault(west)
        val chosen = ZonedDateTime.now().plusMinutes(20).truncatedTo(ChronoUnit.MINUTES)
        sync(chosen.minuteOfDay())
        val pinned = workInfo().nextScheduleTimeMillis
        assertEquals(chosen.millis(), pinned)

        // An hour east it's midnight and 23:20 is gone. The old pin rings at
        // 00:20 on this clock: a day later on the calendar, but still the
        // undelivered 23:20 briefing, and a pin would skip it.
        TimeZone.setDefault(shifted(west, hours = 1))
        sync(chosen.minuteOfDay())

        assertEquals(pinned, workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `turning the briefing off forgets the clock it was pinned on`() {
        sync(inThreeHours.minuteOfDay())
        assertEquals(ZonedDateTime.now().zone.id, DailyBriefingStore(context).pinnedZone())

        DailyBriefingWorker.cancel(context)

        assertEquals(WorkInfo.State.CANCELLED, workInfo().state)
        assertEquals(null, DailyBriefingStore(context).pinnedZone())
    }

    @Test
    fun `a cold start or an import puts a briefing still ahead on the current time`() {
        // Nothing scheduled yet, as when an import turns the briefing on.
        sync(inThreeHours.minuteOfDay())
        assertEquals(inThreeHours.millis(), workInfo().nextScheduleTimeMillis)

        // An import that changes the time.
        sync(inFiveHours.minuteOfDay())
        assertEquals(inFiveHours.millis(), workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a cold start leaves a briefing that is due and waiting on its constraints due`() {
        val dueAt = enqueueDueBriefing()

        sync(inThreeHours.minuteOfDay())

        assertEquals(WorkInfo.State.ENQUEUED, workInfo().state)
        assertEquals(dueAt, workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a schedule that can't be read gets a briefing enqueued when none is`() {
        val before = System.currentTimeMillis()
        DailyBriefingWorker.syncFrom(context, inThreeHours.minuteOfDay(), infos = null)

        // Unpinned, on the initial delay worked out when it was enqueued
        // (rounded down to the millisecond, hence the second of slack).
        val next = workInfo().nextScheduleTimeMillis
        val elapsed = System.currentTimeMillis() - before
        assertEquals(WorkInfo.State.ENQUEUED, workInfo().state)
        assertTrue("next run at $next", next in (inThreeHours.millis() - 1_000)..(inThreeHours.millis() + elapsed))
        // Timed on this clock, so a trip before it runs can tell it came early.
        assertEquals(ZonedDateTime.now().zone.id, DailyBriefingStore(context).pinnedZone())
    }

    @Test
    fun `a schedule that can't be read leaves the one there untouched`() {
        // Never run and unpinned: an UPDATE would count a new delay from
        // when this was first enqueued.
        DailyBriefingWorker.schedule(context, inThreeHours.minuteOfDay())
        val scheduled = workInfo().nextScheduleTimeMillis

        DailyBriefingWorker.syncFrom(context, inFiveHours.minuteOfDay(), infos = null)

        assertEquals(scheduled, workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a schedule that can't be read keeps the clock its pin was set on`() {
        sync(inThreeHours.minuteOfDay())
        val pinned = workInfo().nextScheduleTimeMillis
        val home = ZonedDateTime.now().zone.id

        // After a trip east the kept pin is still on the home clock, and a
        // later cold start has to see that the clock moved.
        TimeZone.setDefault(shifted(TimeZone.getDefault(), hours = 3))
        DailyBriefingWorker.syncFrom(context, inThreeHours.minuteOfDay(), infos = null)
        assertEquals(home, DailyBriefingStore(context).pinnedZone())

        // The chosen time has gone by on this clock: a pin would skip today's.
        sync(inThreeHours.minuteOfDay())
        assertEquals(pinned, workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `a schedule that can't be read leaves a due briefing due`() {
        val dueAt = enqueueDueBriefing()

        DailyBriefingWorker.syncFrom(context, inThreeHours.minuteOfDay(), infos = null)

        assertEquals(dueAt, workInfo().nextScheduleTimeMillis)
    }

    @Test
    fun `the time picker moves a briefing that has already run`() {
        every { networkPolicy.holdsBack(any(), false) } returns true
        DailyBriefingWorker.schedule(context, inThreeHours.minuteOfDay())
        runFirstScheduledBriefing()

        // After the first run the initial delay no longer counts, so only
        // the pin can move it.
        DailyBriefingWorker.schedule(context, inFiveHours.minuteOfDay(), pinNextRun = true)
        assertEquals(inFiveHours.millis(), workInfo().nextScheduleTimeMillis)
    }

    /**
     * Due a minute ago and still held by the battery or Doze, which is
     * exactly when WorkManager may start the process. Returns when it's due.
     */
    private fun enqueueDueBriefing(): Long {
        val dueAt = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)
        val due = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
            .setConstraints(BackgroundWork.periodicConstraints())
            .setNextScheduleTimeOverride(dueAt)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DailyBriefingWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, due)
            .result.get()
        return dueAt
    }

    private fun useBriefingTime(minutes: Int) {
        every { prefs.settings } returns flowOf(
            NimbusSettings(dailyBriefingEnabled = true, dailyBriefingMinutes = minutes),
        )
    }

    private fun sync(minutes: Int) = runBlocking {
        withTimeout(TimeUnit.SECONDS.toMillis(30)) { DailyBriefingWorker.sync(context, minutes) }
    }

    private fun runFirstScheduledBriefing() {
        val id = workInfo().id
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setAllConstraintsMet(id)
        driver.setInitialDelayMet(id)
    }

    /** The test driver treats a pinned run as a later period's, so it also needs the period met. */
    private fun runPinnedBriefing() {
        val id = workInfo().id
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setAllConstraintsMet(id)
        driver.setInitialDelayMet(id)
        driver.setPeriodDelayMet(id)
    }

    private fun workInfo(): WorkInfo =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DailyBriefingWorker.UNIQUE_WORK_NAME)
            .get()
            .single()

    /**
     * A fixed offset where the clock now reads [hour]:[minute], or [hour] and
     * whatever minute it is when [minute] is null (a whole-hour offset).
     */
    private fun zoneWhereItIs(hour: Int, minute: Int? = null): TimeZone {
        val utc = ZonedDateTime.now(ZoneOffset.UTC)
        val utcMinutes = utc.hour * 60 + utc.minute
        val wanted = hour * 60 + (minute ?: utc.minute)
        val offsetMinutes = Math.floorMod(wanted - utcMinutes + 720, 1440) - 720
        return TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(offsetMinutes * 60))
    }

    private fun shifted(zone: TimeZone, hours: Int): TimeZone =
        TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(zone.rawOffset / 1000 + hours * 3600))

    private fun ZonedDateTime.minuteOfDay(): Int = hour * 60 + minute

    private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()
}
