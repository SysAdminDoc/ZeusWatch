package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.repository.BackgroundTraffic
import com.sysadmindoc.nimbus.data.repository.DeliveryFailureReason
import com.sysadmindoc.nimbus.data.repository.DeliveryHealthRepository
import com.sysadmindoc.nimbus.data.repository.DeliverySkipReason
import com.sysadmindoc.nimbus.data.repository.DeliverySurface
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.deliveryFailureReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val TAG = "DailyBriefingWorker"

@HiltWorker
class DailyBriefingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val prefs: UserPreferences,
    private val deliveryHealth: DeliveryHealthRepository,
    private val networkPolicy: BackgroundNetworkPolicy,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = BackgroundTraffic.meter(DeliverySurface.DAILY_BRIEFING, deliveryHealth) { deliver() }
        if (result != Result.retry() && !BackgroundWork.isUserInitiated(inputData)) {
            pinNextRun()
        }
        return result
    }

    /**
     * Puts tomorrow's run back on the chosen time. WorkManager schedules the
     * next periodic run a day after this one finished, so a briefing the
     * system held (battery, Doze, no network) would arrive that much later
     * every day after. Not after a retry: the pin overrides the backoff too,
     * and a failed fetch would wait until tomorrow instead of 15 minutes.
     */
    private suspend fun pinNextRun() {
        val settings = prefs.settings.first()
        if (settings.dailyBriefingEnabled) {
            schedule(applicationContext, settings.dailyBriefingMinutes, pinNextRun = true)
        }
    }

    private suspend fun deliver(): Result {
        val startedAt = System.currentTimeMillis()
        val settings = prefs.settings.first()
        if (!settings.dailyBriefingEnabled) {
            Log.d(TAG, "Daily briefing disabled; skipping")
            deliveryHealth.forget(DeliverySurface.DAILY_BRIEFING)
            return Result.success()
        }
        val userInitiated = BackgroundWork.isUserInitiated(inputData)
        val now = ZonedDateTime.now()
        val store = DailyBriefingStore(applicationContext)
        // Keyed on the chosen time this run belongs to, not today's date: a
        // 23:30 briefing held past midnight is still yesterday's, and tonight's
        // must not count as already sent. Run now skips both checks and isn't
        // written down: it shows the briefing works, it doesn't stand in for
        // the scheduled one.
        val slot = dailyBriefingSlotDate(now, settings.dailyBriefingMinutes)
        val pinnedSlot = dailyBriefingSlotDate(
            now.withZoneSameInstant(pinnedClock(store.pinnedZone(), now)),
            settings.dailyBriefingMinutes,
        )
        if (!userInitiated && slot.isBefore(pinnedSlot)) {
            // Pinned on a clock further east: after a trip west the run comes
            // before the chosen time here. doWork() pins it to that time.
            Log.d(TAG, "Daily briefing for $pinnedSlot came early on this clock; moving it")
            return Result.success()
        }
        val slotDate = slot.toString()
        if (!userInitiated && store.lastDeliveredDate() == slotDate) {
            Log.d(TAG, "Already delivered daily briefing for $slotDate")
            // Counts as working, ahead of the network hold and the fetch: a
            // job that ran twice is neither a problem nor a skipped briefing.
            deliveryHealth.recordSuccess(DeliverySurface.DAILY_BRIEFING, startedAt)
            return Result.success()
        }
        if (networkPolicy.holdsBack(settings, userInitiated)) {
            deliveryHealth.recordSkip(DeliverySurface.DAILY_BRIEFING, DeliverySkipReason.METERED_NETWORK, startedAt)
            // Success, not retry. A retry would spend the budget a real fetch
            // failure on Wi-Fi needs, and the pin in doWork() skips retries,
            // so tomorrow's briefing would land whenever Wi-Fi came back.
            // The day's briefing is skipped and tomorrow's keeps its time.
            return Result.success()
        }
        deliveryHealth.recordAttempt(DeliverySurface.DAILY_BRIEFING, startedAt)

        val loc = prefs.backgroundAlertLocation.first() ?: prefs.lastLocation.first() ?: run {
            Log.d(TAG, "No background alert location; skipping")
            deliveryHealth.recordFailure(
                DeliverySurface.DAILY_BRIEFING,
                DeliveryFailureReason.NO_LOCATION,
                startedAt,
            )
            return Result.success()
        }

        val weatherResult = weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
        val data = weatherResult.getOrNull() ?: run {
            Log.w(TAG, "Weather fetch failed", weatherResult.exceptionOrNull())
            deliveryHealth.recordFailure(
                DeliverySurface.DAILY_BRIEFING,
                weatherResult.exceptionOrNull()?.deliveryFailureReason()
                    ?: DeliveryFailureReason.FORECAST_UNAVAILABLE,
                startedAt,
            )
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }

        val summary = WeatherSummaryEngine.generate(
            current = data.current,
            today = data.daily.firstOrNull(),
            hourly = data.hourly,
            s = settings,
            context = applicationContext,
        )
        val delivered = WeatherNotificationHelper.showDailyBriefing(
            context = applicationContext,
            data = data,
            settings = settings,
            summary = summary,
        )
        if (delivered) {
            if (!userInitiated) store.record(slotDate)
            deliveryHealth.recordSuccess(DeliverySurface.DAILY_BRIEFING, startedAt)
        } else {
            // The notification was built but the system refused to post it,
            // which is what a revoked notification permission looks like.
            deliveryHealth.recordFailure(
                DeliverySurface.DAILY_BRIEFING,
                DeliveryFailureReason.PERMISSION_DENIED,
                startedAt,
            )
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_daily_briefing"

        /**
         * The unique periodic work this worker runs as.
         *
         * Public so the delivery diagnostics can ask WorkManager when it next
         * runs and enqueue a manual run; the panel cannot report on a job whose
         * name it does not know.
         */
        const val UNIQUE_WORK_NAME = "nimbus_daily_briefing"

        /** A one-off run of this worker, for the diagnostics panel's Run now. */
        fun oneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DailyBriefingWorker>()
                // Network only: offline, every provider call fails and Run now
                // manufactures the failure it exists to diagnose. The battery
                // and Wi-Fi holds are for scheduled runs, not this one.
                .setConstraints(BackgroundWork.userInitiatedConstraints())
                .setInputData(BackgroundWork.userInitiatedInput())
                .build()
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PERIOD_HOURS = 24L
        private const val SCHEDULE_READ_TIMEOUT_MS = 5_000L

        /**
         * Cold start and settings import. Pins the next run like a settings
         * change does, except when a run is already due, running or backing
         * off: the process also starts inside a Doze window, and pinning then
         * would push a 07:00 briefing still waiting on its constraints, or
         * the retry of a failed one, to tomorrow. After the clock moved to
         * another offset, the pin may only move the next run within its own
         * day (see [pinLeavesNoRunBehind]).
         */
        suspend fun sync(context: Context, minutesAfterMidnight: Int) {
            val infos = try {
                withTimeoutOrNull(SCHEDULE_READ_TIMEOUT_MS) {
                    WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).first()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read the briefing schedule", e)
                null
            }
            syncFrom(context, minutesAfterMidnight, infos)
        }

        /**
         * [sync] once the schedule is read. [infos] is null when it couldn't
         * be: whatever is scheduled stays exactly as it is (KEEP), and the
         * briefing is enqueued only when nothing is, so an import that turned
         * it on still gets a job.
         */
        internal fun syncFrom(
            context: Context,
            minutesAfterMidnight: Int,
            infos: List<WorkInfo>?,
            now: ZonedDateTime = ZonedDateTime.now(),
        ) {
            if (infos == null) {
                schedule(context, minutesAfterMidnight, now = now, policy = ExistingPeriodicWorkPolicy.KEEP)
                // A job enqueued just now timed its first run on this clock,
                // and one kept with no zone on record is read on it anyway.
                // A zone on record stays: it normally belongs to the run
                // still pending, and the next readable sync rewrites it.
                val store = DailyBriefingStore(context)
                if (store.pinnedZone() == null) store.recordPinnedZone(now.zone.id)
                return
            }
            val pin = pinLeavesNoRunBehind(
                infos = infos,
                now = now,
                minutesAfterMidnight = minutesAfterMidnight,
                pinnedZone = DailyBriefingStore(context).pinnedZone(),
            )
            schedule(context, minutesAfterMidnight, pinNextRun = pin, now = now)
        }

        /**
         * @param pinNextRun set the next run to the next [minutesAfterMidnight]
         *   outright. Without it, UPDATE keeps whatever pin is set; a
         *   briefing that has never run and has no pin then counts the new
         *   initial delay from when it was first enqueued.
         * @param policy KEEP leaves any live briefing untouched, pin included.
         */
        fun schedule(
            context: Context,
            minutesAfterMidnight: Int,
            pinNextRun: Boolean = false,
            now: ZonedDateTime = ZonedDateTime.now(),
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
        ) {
            val delay = initialDelayUntilDailyBriefing(now, minutesAfterMidnight)
            val constraints = BackgroundWork.periodicConstraints()

            // No flex window: the pin sets the time, and a flex only moved an
            // unpinned first run (interval - flex) later, 22 hours here.
            val builder = PeriodicWorkRequestBuilder<DailyBriefingWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            if (pinNextRun) {
                builder.setNextScheduleTimeOverride(
                    nextDailyBriefingAt(now, minutesAfterMidnight).toInstant().toEpochMilli(),
                )
                DailyBriefingStore(context).recordPinnedZone(now.zone.id)
            }
            val request = builder.build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            // The pin is gone, and a zone left behind could make the first
            // run after turning it back on read as early and skip.
            DailyBriefingStore(context).forgetPinnedZone()
        }
    }
}

/**
 * Whether pinning the next briefing to the next [minutesAfterMidnight] can't
 * lose one: true when nothing is scheduled, or the scheduled run is still
 * ahead and isn't a retry. Once the clock has moved off [pinnedZone]'s
 * offset, the pin may move that run within its own day but never past it:
 * after a trip east the day's chosen time may already be gone here, and a
 * pin would skip that briefing. After a trip west the old pin would come
 * hours early, and moving it to the chosen time fixes that.
 */
internal fun pinLeavesNoRunBehind(
    infos: List<WorkInfo>,
    now: ZonedDateTime,
    minutesAfterMidnight: Int,
    pinnedZone: String? = null,
): Boolean {
    val live = infos.firstOrNull { !it.state.isFinished } ?: return true
    val nextRun = Instant.ofEpochMilli(live.nextScheduleTimeMillis)
    val waiting = live.state == WorkInfo.State.ENQUEUED &&
        live.runAttemptCount == 0 &&
        nextRun.isAfter(now.toInstant())
    if (!waiting || !clockMovedSincePin(pinnedZone, now)) return waiting
    // The run's day read on the clock it was pinned on, where it lands on the
    // chosen time. On this clock a run moved west reads as the day before's,
    // and a 23:30 one moved east past midnight as the day after's.
    val runSlot = dailyBriefingSlotDate(nextRun.atZone(pinnedClock(pinnedZone, now)), minutesAfterMidnight)
    val pinSlot = nextDailyBriefingAt(now, minutesAfterMidnight).toLocalDate()
    return !pinSlot.isAfter(runSlot)
}

/** The zone the next run was pinned in, or [now]'s when there's no pin yet or this device doesn't know it. */
internal fun pinnedClock(pinnedZone: String?, now: ZonedDateTime): ZoneId {
    if (pinnedZone == null) return now.zone
    return try {
        ZoneId.of(pinnedZone)
    } catch (ignored: DateTimeException) {
        now.zone
    }
}

/**
 * Whether the phone's clock reads differently from the one the next run was
 * pinned on. Offsets, not zone names, since Toronto and New York share a
 * clock. A zone this device no longer knows counts as moved.
 */
internal fun clockMovedSincePin(pinnedZone: String?, now: ZonedDateTime): Boolean {
    if (pinnedZone == null) return false
    return try {
        ZoneId.of(pinnedZone).rules.getOffset(now.toInstant()) != now.offset
    } catch (ignored: DateTimeException) {
        true
    }
}

/**
 * The day whose briefing a run at [now] delivers: today once the chosen time
 * has come, otherwise yesterday's, held past midnight. Compared as instants,
 * so on the night the clocks go back a run in the repeated hour after the
 * chosen time still counts as today's.
 */
internal fun dailyBriefingSlotDate(now: ZonedDateTime, minutesAfterMidnight: Int): LocalDate {
    val normalizedMinutes = minutesAfterMidnight.coerceIn(0, MINUTES_IN_DAY - 1)
    val time = LocalTime.of(normalizedMinutes / 60, normalizedMinutes % 60)
    val todays = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
    return if (now.isBefore(todays)) now.toLocalDate().minusDays(1) else now.toLocalDate()
}

/** Real time until the next briefing, so a night that changes the clocks counts its extra or missing hour. */
internal fun initialDelayUntilDailyBriefing(now: ZonedDateTime, minutesAfterMidnight: Int): Duration =
    Duration.between(now, nextDailyBriefingAt(now, minutesAfterMidnight))

/**
 * The next time the briefing is due, strictly after [now]. Built from the
 * zone's own rules, so a night that changes the clocks still lands on the
 * chosen wall-clock time (a time the spring change skips moves forward).
 */
internal fun nextDailyBriefingAt(now: ZonedDateTime, minutesAfterMidnight: Int): ZonedDateTime {
    val normalizedMinutes = minutesAfterMidnight.coerceIn(0, MINUTES_IN_DAY - 1)
    val time = LocalTime.of(normalizedMinutes / 60, normalizedMinutes % 60)
    val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
    return if (now.isBefore(today)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
}

internal class DailyBriefingStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_daily_briefing", Context.MODE_PRIVATE)

    fun lastDeliveredDate(): String? = prefs.getString(KEY_LAST_DATE, null)

    fun record(date: String) {
        prefs.edit().putString(KEY_LAST_DATE, date).apply()
    }

    /** The clock the next run is read on, or null when none is on record (never pinned, or turned off). */
    fun pinnedZone(): String? = prefs.getString(KEY_PINNED_ZONE, null)

    fun recordPinnedZone(zoneId: String) {
        prefs.edit().putString(KEY_PINNED_ZONE, zoneId).apply()
    }

    fun forgetPinnedZone() {
        prefs.edit().remove(KEY_PINNED_ZONE).apply()
    }

    companion object {
        private const val KEY_LAST_DATE = "last_delivered_date"
        private const val KEY_PINNED_ZONE = "pinned_zone"
    }
}

private const val MINUTES_IN_DAY = 24 * 60
