package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.repository.DeliveryFailureReason
import com.sysadmindoc.nimbus.data.repository.DeliveryHealthRepository
import com.sysadmindoc.nimbus.data.repository.DeliverySurface
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import com.sysadmindoc.nimbus.data.repository.deliveryFailureReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val TAG = "DailyBriefingWorker"

@HiltWorker
class DailyBriefingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val prefs: UserPreferences,
    private val deliveryHealth: DeliveryHealthRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val startedAt = System.currentTimeMillis()
        val settings = prefs.settings.first()
        if (!settings.dailyBriefingEnabled) {
            Log.d(TAG, "Daily briefing disabled; skipping")
            deliveryHealth.forget(DeliverySurface.DAILY_BRIEFING)
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

        val referenceDate = weatherReferenceDate(data).toString()
        val store = DailyBriefingStore(applicationContext)
        if (store.lastDeliveredDate() == referenceDate) {
            Log.d(TAG, "Already delivered daily briefing for $referenceDate")
            // Already delivered today counts as working: the panel should not
            // report a problem because the job ran twice.
            deliveryHealth.recordSuccess(DeliverySurface.DAILY_BRIEFING, startedAt)
            return Result.success()
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
            store.record(referenceDate)
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
                .build()
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PERIOD_HOURS = 24L
        private const val FLEX_HOURS = 2L

        fun schedule(context: Context, minutesAfterMidnight: Int) {
            val delay = initialDelayUntilDailyBriefing(
                now = LocalDateTime.now(),
                minutesAfterMidnight = minutesAfterMidnight,
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyBriefingWorker>(
                PERIOD_HOURS, TimeUnit.HOURS,
                FLEX_HOURS, TimeUnit.HOURS,
            )
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

internal fun initialDelayUntilDailyBriefing(
    now: LocalDateTime,
    minutesAfterMidnight: Int,
): Duration {
    val normalizedMinutes = minutesAfterMidnight.coerceIn(0, MINUTES_IN_DAY - 1)
    val targetToday = now.toLocalDate()
        .atStartOfDay()
        .plusMinutes(normalizedMinutes.toLong())
    val target = if (now.isBefore(targetToday)) targetToday else targetToday.plusDays(1)
    return Duration.between(now, target)
}

private class DailyBriefingStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_daily_briefing", Context.MODE_PRIVATE)

    fun lastDeliveredDate(): String? = prefs.getString(KEY_LAST_DATE, null)

    fun record(date: String) {
        prefs.edit().putString(KEY_LAST_DATE, date).apply()
    }

    companion object {
        private const val KEY_LAST_DATE = "last_delivered_date"
    }
}

private const val MINUTES_IN_DAY = 24 * 60
