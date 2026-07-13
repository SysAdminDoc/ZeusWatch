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
 * Deduplicates per (alert type, severity) per forecast-local calendar date:
 * a same-day escalation (ADVISORY → WARNING) still notifies, but a same-day
 * downgrade (WARNING → ADVISORY) or repeat stays suppressed.
 *
 * Silently skips if:
 *  - user toggled `healthAlertsEnabled` off
 *  - no GPS-derived background alert location known
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

        val loc = prefs.backgroundAlertLocation.first() ?: run {
            Log.d(TAG, "No background alert location; skipping")
            return Result.success()
        }

        val weatherResult = weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
        val data = weatherResult.getOrNull() ?: run {
            Log.w(TAG, "Weather fetch failed", weatherResult.exceptionOrNull())
            // Retry transient failures with the configured backoff (bounded);
            // give up to the next hourly tick after that.
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
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
        val today = weatherReferenceDate(data).toString()

        for (alert in alerts) {
            // Atomically claim the slot before notifying so two concurrent runs
            // can't both notify. Key includes severity so a same-day escalation
            // isn't suppressed: a morning ADVISORY must not swallow an afternoon
            // WARNING; the reverse (ADVISORY after WARNING) stays suppressed.
            if (!store.claimIfNew(alert.type, alert.severity, today)) {
                Log.d(TAG, "Already notified for ${alert.type.name}:${alert.severity.name} (or higher) today")
                continue
            }

            // Slot already claimed above; notify (claim-before-notify mirrors the
            // custom-alert worker so a rare delivery failure won't re-fire today).
            AlertNotificationHelper.showHealthNotification(
                context = applicationContext,
                type = alert.type,
                title = applicationContext.getString(alert.type.labelRes),
                body = applicationContext.healthAlertText(alert.messageRes, alert.messageArgs),
                detail = alert.detailRes?.let { applicationContext.healthAlertText(it, alert.detailArgs) } ?: "",
                severity = alert.severity,
            )
        }

        // Prune old entries (keep last 7 days)
        store.prune(today)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_health_alert"
        private const val MAX_RETRY_ATTEMPTS = 3

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
                // UPDATE (not KEEP): re-scheduling with a changed interval or
                // constraints must take effect on existing installs; KEEP makes
                // every re-schedule after the first a silent no-op.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

private fun Context.healthAlertText(resId: Int, args: List<Any>): String {
    return if (args.isEmpty()) {
        getString(resId)
    } else {
        getString(resId, *args.toTypedArray())
    }
}

/**
 * SharedPreferences-backed dedupe store for health alert notifications.
 * Keys are "TYPE:SEVERITY:YYYY-MM-DD" — each (type, severity) fires at most
 * once per day, and a lower severity is also suppressed once a higher one
 * has fired the same day. (Legacy "TYPE:YYYY-MM-DD" keys from older builds
 * are tolerated by the pruner and simply age out.)
 */
private class HealthNotificationStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_health_alerts", Context.MODE_PRIVATE)

    /**
     * True if [severity] or any *more severe* tier (lower [HealthSeverity]
     * ordinal) was already notified for [type] on [date]. A WARNING after an
     * ADVISORY returns false (escalation re-fires); an ADVISORY after a
     * WARNING returns true (downgrade stays quiet).
     */
    fun isNotifiedAtOrAbove(type: HealthAlertType, severity: HealthSeverity, date: String): Boolean =
        HealthSeverity.entries
            .filter { it.ordinal <= severity.ordinal }
            .any { prefs.getBoolean("${type.name}:${it.name}:$date", false) }

    /**
     * Atomically claim the (type, severity, date) slot: if not already notified
     * at or above [severity], record it and return true. Serialized so two
     * concurrent worker runs can't both pass the check and double-notify.
     */
    fun claimIfNew(type: HealthAlertType, severity: HealthSeverity, date: String): Boolean =
        synchronized(LOCK) {
            if (isNotifiedAtOrAbove(type, severity, date)) return@synchronized false
            prefs.edit().putBoolean("${type.name}:${severity.name}:$date", true).apply()
            true
        }

    /** Remove entries older than 7 days to prevent unbounded growth. */
    fun prune(referenceDate: String) = synchronized(LOCK) {
        val cutoff = LocalDate.parse(referenceDate).minusDays(7).toString()
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            // The trailing segment is always the date, for both the current
            // "TYPE:SEVERITY:date" shape and legacy "TYPE:date" keys.
            val datePart = key.substringAfterLast(":", "")
            if (datePart.isNotEmpty() && datePart < cutoff) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    companion object {
        // Static so it serializes claim/prune across separate store instances
        // (one per worker run) sharing the same process + SharedPreferences.
        private val LOCK = Any()
    }
}
