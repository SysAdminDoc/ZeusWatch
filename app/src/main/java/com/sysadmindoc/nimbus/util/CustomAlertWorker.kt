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
import com.sysadmindoc.nimbus.data.repository.AirQualityRepository
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that evaluates user-defined custom alert rules against
 * the latest forecast for the user's GPS-derived background alert location, and notifies
 * whenever a rule's threshold is crossed.
 *
 * Each (rule-id, threshold, yyyy-MM-dd) triple is deduped against a
 * SharedPreferences-backed seen-set so the same rule doesn't re-fire every
 * hour on the same forecast-local day — while still re-arming the same day
 * if the user edits the rule's threshold. The dedupe set is trimmed to the
 * last 7 days to keep the file small.
 */
private const val TAG = "CustomAlertWorker"

@HiltWorker
class CustomAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val airQualityRepository: AirQualityRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rules = prefs.customAlertRules.first()
        if (rules.none { it.enabled }) return Result.success()

        val settings = prefs.settings.first()
        val loc = prefs.backgroundAlertLocation.first() ?: return Result.success()

        val weatherResult = weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name)
        val weather = weatherResult.getOrNull()
            ?: return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()

        val airQuality = if (rules.any { it.enabled && it.metric == CustomAlertMetric.AQI_NOW }) {
            airQualityRepository.getAirQuality(loc.latitude, loc.longitude).getOrNull()
        } else null

        val triggered = evaluateCustomAlertRules(rules, weather, airQuality)
        if (triggered.isEmpty()) return Result.success()

        val store = CustomAlertDedupeStore(applicationContext)
        val today = weatherReferenceDate(weather).toString()
        store.pruneOld(today)

        for (hit in triggered) {
            // Threshold is part of the key so editing a rule re-arms
            // evaluation the same day instead of staying muted until tomorrow.
            val dedupeKey = "${hit.rule.id}:${"%.6f".format(java.util.Locale.US, hit.rule.thresholdCanonical)}:$today"
            if (!store.markAndCheckNew(dedupeKey)) continue
            val (title, body) = formatTriggeredAlert(applicationContext, hit, settings)
            val delivered = AlertNotificationHelper.showCustomAlertNotification(
                context = applicationContext,
                ruleKey = hit.rule.id,
                title = title,
                body = body,
            )
            if (!delivered) {
                store.remove(dedupeKey)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_custom_alert"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CustomAlertWorker>(
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

/**
 * Thin SharedPreferences-backed set of `"$ruleId:$threshold:$yyyy-MM-dd"`
 * keys that have already been notified, so an unchanged rule fires at most
 * once per calendar day.
 */
private class CustomAlertDedupeStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_custom_alert_dedupe", Context.MODE_PRIVATE)

    /** Returns true if [key] was newly added (i.e. not already notified). */
    fun markAndCheckNew(key: String): Boolean = synchronized(LOCK) {
        val seen = prefs.getStringSet(KEY_SET, emptySet()).orEmpty().toMutableSet()
        if (!seen.add(key)) return@synchronized false
        prefs.edit().putStringSet(KEY_SET, seen).apply()
        true
    }

    fun remove(key: String) = synchronized(LOCK) {
        val seen = prefs.getStringSet(KEY_SET, emptySet()).orEmpty().toMutableSet()
        if (seen.remove(key)) {
            prefs.edit().putStringSet(KEY_SET, seen).apply()
        }
    }

    /** Drop dedupe keys older than 7 days so the set doesn't grow unbounded. */
    fun pruneOld(referenceDate: String) = synchronized(LOCK) {
        val cutoff = LocalDate.parse(referenceDate).minusDays(7).toString()
        val current = prefs.getStringSet(KEY_SET, emptySet()).orEmpty()
        val keep = current.filter { key ->
            // Keys look like "$ruleId:$threshold:$yyyy-MM-dd" (legacy builds
            // wrote "$ruleId:$yyyy-MM-dd") — either way the LAST segment is
            // the date; keep anything whose trailing date is >= cutoff.
            // Non-matching keys are dropped defensively.
            val date = key.substringAfterLast(':', "")
            date.length == 10 && date >= cutoff
        }.toSet()
        if (keep.size != current.size) {
            prefs.edit().putStringSet(KEY_SET, keep).apply()
        }
    }

    companion object {
        private const val KEY_SET = "notified_keys"

        // Static so it serializes the non-atomic get/modify/put across separate
        // store instances (each worker run makes its own). Workers share one
        // process, so a JVM monitor is sufficient; without it a concurrent run can
        // drop a key and re-fire a deduped alert.
        private val LOCK = Any()
    }
}
