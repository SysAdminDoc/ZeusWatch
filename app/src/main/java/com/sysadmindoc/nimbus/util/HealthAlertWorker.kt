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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that evaluates health-related weather triggers
 * (migraine pressure changes, respiratory humidity, arthritis temperature swings)
 * and fires proactive notifications.
 *
 * Runs every 1 hour. Uses the same [HealthAlertEvaluator] logic as the UI card.
 * Deduplicates per alert type per calendar date — each type fires at most once/day.
 *
 * Silently skips if:
 *  - user toggled `healthAlertsEnabled` off
 *  - no last location known
 *  - POST_NOTIFICATIONS permission not granted
 *  - weather fetch fails
 *  - already notified for this alert type today
 */
private const val TAG = "HealthAlertWorker"

@HiltWorker
class HealthAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()
        if (!settings.healthAlertsEnabled) {
            Log.d(TAG, "Health alerts disabled; skipping")
            return Result.success()
        }

        val loc = prefs.lastLocation.first() ?: run {
            Log.d(TAG, "No last location; skipping")
            return Result.success()
        }

        val weatherResult = weatherRepository.getWeatherDirect(loc.latitude, loc.longitude, null)
        val data = weatherResult.getOrNull() ?: run {
            Log.w(TAG, "Weather fetch failed", weatherResult.exceptionOrNull())
            return Result.success() // Don't retry; next hourly run will try again
        }

        val alerts = HealthAlertEvaluator.evaluate(
            hourly = data.hourly,
            pressureThresholdHpa = settings.migrainePressureThreshold,
            enableMigraine = settings.migraineAlerts,
        )

        if (alerts.isEmpty()) {
            Log.d(TAG, "No health alerts triggered")
            return Result.success()
        }

        val store = HealthNotificationStore(applicationContext)
        val today = LocalDate.now().toString()

        for (alert in alerts) {
            val key = "${alert.type.name}:$today"
            if (store.isNotified(key)) {
                Log.d(TAG, "Already notified for $key today")
                continue
            }

            AlertNotificationHelper.showHealthNotification(
                context = applicationContext,
                title = alert.type.label,
                body = alert.message,
                detail = alert.detail,
                severity = alert.severity,
            )
            store.record(key)
        }

        // Prune old entries (keep last 7 days)
        store.prune()

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_health_alert"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<HealthAlertWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/**
 * SharedPreferences-backed dedupe store for health alert notifications.
 * Keys are "TYPE:YYYY-MM-DD" — each alert type fires at most once per day.
 */
private class HealthNotificationStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_health_alerts", Context.MODE_PRIVATE)

    fun isNotified(key: String): Boolean = prefs.getBoolean(key, false)

    fun record(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }

    /** Remove entries older than 7 days to prevent unbounded growth. */
    fun prune() {
        val cutoff = LocalDate.now().minusDays(7).toString()
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            val datePart = key.substringAfter(":", "")
            if (datePart.isNotEmpty() && datePart < cutoff) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
}
