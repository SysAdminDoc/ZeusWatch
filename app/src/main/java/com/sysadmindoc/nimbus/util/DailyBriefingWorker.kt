package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()
        if (!settings.dailyBriefingEnabled) {
            Log.d(TAG, "Daily briefing disabled; skipping")
            return Result.success()
        }

        val loc = prefs.lastLocation.first() ?: run {
            Log.d(TAG, "No last location; skipping")
            return Result.success()
        }

        val weatherResult = weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
        val data = weatherResult.getOrNull() ?: run {
            Log.w(TAG, "Weather fetch failed", weatherResult.exceptionOrNull())
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }

        val referenceDate = weatherReferenceDate(data).toString()
        val store = DailyBriefingStore(applicationContext)
        if (store.lastDeliveredDate() == referenceDate) {
            Log.d(TAG, "Already delivered daily briefing for $referenceDate")
            return Result.success()
        }

        val summary = WeatherSummaryEngine.generate(
            current = data.current,
            today = data.daily.firstOrNull(),
            hourly = data.hourly,
            s = settings,
        )
        val delivered = WeatherNotificationHelper.showDailyBriefing(
            context = applicationContext,
            data = data,
            settings = settings,
            summary = summary,
        )
        if (delivered) {
            store.record(referenceDate)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_daily_briefing"
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
