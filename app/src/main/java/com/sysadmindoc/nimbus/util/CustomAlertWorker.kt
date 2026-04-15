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
 * Periodic worker that evaluates user-defined custom alert rules against
 * the latest forecast for the user's last-known location, and notifies
 * whenever a rule's threshold is crossed.
 *
 * Each (rule-id, yyyy-MM-dd) pair is deduped against a SharedPreferences-
 * backed seen-set so the same rule doesn't re-fire every hour on the same
 * forecast-local day. The dedupe set is trimmed to the last 7 days to keep
 * the file small.
 */
private const val TAG = "CustomAlertWorker"

@HiltWorker
class CustomAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rules = prefs.customAlertRules.first()
        if (rules.none { it.enabled }) return Result.success()

        val settings = prefs.settings.first()
        val loc = prefs.lastLocation.first() ?: return Result.success()

        val weather = weatherRepository.getWeather(loc.latitude, loc.longitude, loc.name).getOrNull()
            ?: return Result.success()

        val triggered = evaluateCustomAlertRules(rules, weather)
        if (triggered.isEmpty()) return Result.success()

        val store = CustomAlertDedupeStore(applicationContext)
        val today = weatherReferenceDate(weather).toString()
        store.pruneOld(today)

        for (hit in triggered) {
            val dedupeKey = "${hit.rule.id}:$today"
            if (!store.markAndCheckNew(dedupeKey)) continue
            val (title, body) = formatTriggeredAlert(hit, settings)
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
 * Thin SharedPreferences-backed set of `"$ruleId:$yyyy-MM-dd"` keys that
 * have already been notified, so a rule fires at most once per calendar day.
 */
private class CustomAlertDedupeStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_custom_alert_dedupe", Context.MODE_PRIVATE)

    /** Returns true if [key] was newly added (i.e. not already notified). */
    fun markAndCheckNew(key: String): Boolean {
        val seen = prefs.getStringSet(KEY_SET, emptySet()).orEmpty().toMutableSet()
        if (!seen.add(key)) return false
        prefs.edit().putStringSet(KEY_SET, seen).apply()
        return true
    }

    fun remove(key: String) {
        val seen = prefs.getStringSet(KEY_SET, emptySet()).orEmpty().toMutableSet()
        if (seen.remove(key)) {
            prefs.edit().putStringSet(KEY_SET, seen).apply()
        }
    }

    /** Drop dedupe keys older than 7 days so the set doesn't grow unbounded. */
    fun pruneOld(referenceDate: String) {
        val cutoff = LocalDate.parse(referenceDate).minusDays(7).toString()
        val current = prefs.getStringSet(KEY_SET, emptySet()).orEmpty()
        val keep = current.filter { key ->
            // Keys look like "$ruleId:$yyyy-MM-dd"; keep anything whose trailing
            // date is >= cutoff. Non-matching keys are dropped defensively.
            val date = key.substringAfterLast(':', "")
            date.length == 10 && date >= cutoff
        }.toSet()
        if (keep.size != current.size) {
            prefs.edit().putStringSet(KEY_SET, keep).apply()
        }
    }

    companion object {
        private const val KEY_SET = "notified_keys"
    }
}
