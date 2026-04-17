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
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that fires proactive precipitation notifications
 * for the user's current location ("Rain starts in 15 min" / "Rain stops soon").
 *
 * Behavior:
 *  - Runs every 15 minutes (matches the Open-Meteo minutely_15 cadence).
 *  - Pulls the last-known user location from [UserPreferences.lastLocation].
 *  - Calls [WeatherRepository.getMinutelyPrecipitation] and passes the result
 *    through the pure-function [detectNowcastTransition] for classification.
 *  - Fires at most one notification per run. The "transition signature"
 *    (state + bucket timestamp) is persisted so subsequent runs don't
 *    repeatedly notify the same transition.
 *  - Additionally enforces a [MIN_SECONDS_BETWEEN_NOTIFICATIONS] cooldown so
 *    a storm flipping between wet/dry/wet every 15 min doesn't spam.
 *
 *  Silently skips if:
 *    - user toggled `nowcastingAlerts` off
 *    - no last location is known yet
 *    - POST_NOTIFICATIONS permission not granted
 *    - the minutely API returns nothing usable
 */
private const val TAG = "NowcastAlertWorker"

@HiltWorker
class NowcastAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherRepository: WeatherRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()
        if (!settings.nowcastingAlerts) {
            Log.d(TAG, "Nowcast alerts disabled; skipping")
            return Result.success()
        }
        val loc = prefs.lastLocation.first() ?: run {
            Log.d(TAG, "No last location; skipping")
            return Result.success()
        }

        val seriesResult = weatherRepository.getMinutelyPrecipitation(loc.latitude, loc.longitude)
        val series = seriesResult.getOrNull()
        if (series.isNullOrEmpty()) {
            Log.d(TAG, "No minutely series; skipping")
            // Not a retriable failure from the user's POV — the next 15-minute
            // run will try again.
            return Result.success()
        }

        // Minutely buckets are returned in the forecast location's local time.
        // Anchoring against the device clock breaks remote-location nowcasts.
        val now = nowcastReferenceTime(series)
        val transition = detectNowcastTransition(series, now) ?: run {
            Log.d(TAG, "No transition in window")
            return Result.success()
        }

        val store = NowcastNotificationStore(applicationContext)
        val signature = transitionSignature(transition)
        if (signature == store.lastSignature()) {
            Log.d(TAG, "Already notified for $signature")
            return Result.success()
        }
        val nowEpoch = System.currentTimeMillis() / 1000
        if (nowEpoch - store.lastNotifiedAtEpoch() < MIN_SECONDS_BETWEEN_NOTIFICATIONS) {
            Log.d(TAG, "Cooldown active; skipping")
            return Result.success()
        }

        val (title, body) = formatNowcastNotification(transition)
        val delivered = AlertNotificationHelper.showNowcastNotification(applicationContext, title, body)
        if (delivered) {
            store.record(signature, nowEpoch)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_nowcast_alert"

        /** Min seconds between two back-to-back nowcast notifications. 45 min. */
        private const val MIN_SECONDS_BETWEEN_NOTIFICATIONS = 45L * 60L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NowcastAlertWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
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

internal fun nowcastReferenceTime(series: List<com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation>): LocalDateTime {
    // Minutely API returns buckets in the forecast LOCATION's local time
    // (timezone=auto). We anchor "now" on the earliest bucket so remote-location
    // nowcasts aren't broken by the device clock being in a different timezone.
    return series.minByOrNull { it.time }?.time ?: LocalDateTime.now()
}

/**
 * Stable opaque identifier for a transition, so the same bucket isn't
 * renotified across back-to-back worker runs.
 */
internal fun transitionSignature(t: NowcastTransition): String = when (t) {
    is NowcastTransition.RainStarting -> "start:${t.startsAt}"
    is NowcastTransition.RainStopping -> "stop:${t.endsAt}"
}

/** Build the user-facing title + body for a transition. */
internal fun formatNowcastNotification(t: NowcastTransition): Pair<String, String> = when (t) {
    is NowcastTransition.RainStarting -> {
        val mins = t.minutesUntil.coerceAtLeast(1)
        val title = if (mins <= 5) "Rain starting soon" else "Rain in about $mins min"
        val intensity = when {
            t.peakMm >= 2.5 -> "heavy"
            t.peakMm >= 1.0 -> "steady"
            else -> "light"
        }
        title to "Expect $intensity rain starting in about $mins minutes at your current location."
    }
    is NowcastTransition.RainStopping -> {
        val mins = t.minutesUntil.coerceAtLeast(1)
        val title = if (mins <= 5) "Rain stopping soon" else "Rain easing in about $mins min"
        title to "Current rain looks like it'll ease off in about $mins minutes."
    }
}

/**
 * Small SharedPreferences-backed store used by the worker to dedupe
 * notifications across runs. Kept as a thin wrapper so the worker body
 * stays focused on logic.
 */
private class NowcastNotificationStore(context: Context) {
    private val prefs = context.getSharedPreferences("nimbus_nowcast_alerts", Context.MODE_PRIVATE)

    fun lastSignature(): String? = prefs.getString(KEY_SIGNATURE, null)
    fun lastNotifiedAtEpoch(): Long = prefs.getLong(KEY_TIMESTAMP, 0L)

    fun record(signature: String, epochSeconds: Long) {
        prefs.edit()
            .putString(KEY_SIGNATURE, signature)
            .putLong(KEY_TIMESTAMP, epochSeconds)
            .apply()
    }

    companion object {
        private const val KEY_SIGNATURE = "last_signature"
        private const val KEY_TIMESTAMP = "last_notified_at"
    }
}
