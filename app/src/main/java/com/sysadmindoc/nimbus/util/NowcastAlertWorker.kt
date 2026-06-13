package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that fires proactive precipitation notifications
 * for the user's GPS-derived background alert location ("Rain starts in 15 min" / "Rain stops soon").
 *
 * Behavior:
 *  - Runs every 15 minutes (matches the Open-Meteo minutely_15 cadence).
 *  - Pulls the GPS-derived alert anchor from [UserPreferences.backgroundAlertLocation].
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
 *    - no GPS-derived background alert location is known yet
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
        val loc = prefs.backgroundAlertLocation.first() ?: run {
            Log.d(TAG, "No background alert location; skipping")
            return Result.success()
        }

        val seriesResult = weatherRepository.getMinutelyPrecipitation(loc.latitude, loc.longitude)
        if (seriesResult.isFailure) {
            // A genuine fetch failure (network/provider) — retry with backoff so
            // an incoming rain transition isn't missed for a full period.
            Log.w(TAG, "Minutely fetch failed", seriesResult.exceptionOrNull())
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }
        val series = seriesResult.getOrNull()
        if (series.isNullOrEmpty()) {
            // Legitimately empty (no precip in the window) — nothing to do.
            Log.d(TAG, "No minutely series; skipping")
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
        val isCountdownUpdate = signature == store.lastSignature()
        if (!isCountdownUpdate) {
            val nowEpoch = System.currentTimeMillis() / 1000
            val sinceLast = nowEpoch - store.lastNotifiedAtEpoch()
            if (sinceLast < 0) {
                Log.w(TAG, "Clock rollback detected (delta=${sinceLast}s); resetting cooldown")
                store.clearCooldown()
            } else if (sinceLast < MIN_SECONDS_BETWEEN_NOTIFICATIONS) {
                Log.d(TAG, "Cooldown active; skipping")
                return Result.success()
            }
        }

        val (title, body) = formatNowcastNotification(applicationContext, transition, loc.name)
        val delivered = AlertNotificationHelper.showNowcastNotification(applicationContext, title, body, series)
        if (delivered) {
            val nowEpoch = System.currentTimeMillis() / 1000
            store.record(signature, if (isCountdownUpdate) store.lastNotifiedAtEpoch() else nowEpoch)
            scheduleRecheck(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nimbus_nowcast_alert"
        private const val RECHECK_WORK_NAME = "nimbus_nowcast_recheck"
        private const val MAX_RETRY_ATTEMPTS = 3

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
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(RECHECK_WORK_NAME)
        }

        private fun scheduleRecheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<NowcastAlertWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                RECHECK_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

/**
 * How far (in minutes) the wall clock may sit *outside* the series window and
 * still be trusted as the anchor. Beyond this, the buckets are in a different
 * local-time frame (remote location) and mixing frames would skew
 * `minutesUntil` by up to the timezone offset.
 */
internal const val NOWCAST_CLOCK_SKEW_TOLERANCE_MIN = 5L

internal fun nowcastReferenceTime(series: List<com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation>): LocalDateTime {
    // Minutely API returns buckets in the forecast LOCATION's local time
    // (timezone=auto). When the wall clock falls inside (or within a few
    // minutes of) the series window, use it directly — anchoring on the
    // earliest bucket unconditionally meant `minutesUntil` was counted from
    // the *oldest* bucket and reported "rain in 30 min" when the
    // series-relative truth was "rain in 5 min".
    //
    // For buckets that don't align with the device clock (a viewed location
    // even one timezone away) we fall back to the earliest bucket so
    // `detectNowcastTransition` walks the series relative to itself rather
    // than against an unrelated wall clock. The previous ±60-minute earliest-
    // bucket tolerance silently accepted exactly that 1-hour-offset case.
    if (series.isEmpty()) return LocalDateTime.now()
    val earliestTime = series.minOf { it.time }
    val latestTime = series.maxOf { it.time }
    val wallClock = LocalDateTime.now()
    val withinWindow = !wallClock.isBefore(earliestTime) && !wallClock.isAfter(latestTime)
    val minutesFromWindow = minOf(
        kotlin.math.abs(java.time.Duration.between(earliestTime, wallClock).toMinutes()),
        kotlin.math.abs(java.time.Duration.between(latestTime, wallClock).toMinutes()),
    )
    return if (withinWindow || minutesFromWindow <= NOWCAST_CLOCK_SKEW_TOLERANCE_MIN) {
        wallClock
    } else {
        earliestTime
    }
}

/**
 * Stable opaque identifier for a transition, so the same bucket isn't
 * renotified across back-to-back worker runs.
 */
internal fun transitionSignature(t: NowcastTransition): String = when (t) {
    is NowcastTransition.RainStarting -> "start:${t.startsAt}"
    is NowcastTransition.RainStopping -> "stop:${t.endsAt}"
}

/**
 * Build the user-facing title + body for a transition, localized via string
 * resources. [locationName] is the saved location the worker is anchored to
 * (it monitors the last *viewed* location, not necessarily where the device
 * is right now) — when available it's named in the body; otherwise the copy
 * stays neutral rather than claiming "your current location".
 */
internal fun formatNowcastNotification(
    context: Context,
    t: NowcastTransition,
    locationName: String? = null,
): Pair<String, String> = when (t) {
    is NowcastTransition.RainStarting -> {
        val mins = t.minutesUntil.coerceAtLeast(1)
        val hasLocation = !locationName.isNullOrBlank()
        val title = when {
            mins <= 5 && hasLocation -> context.getString(R.string.nowcast_notif_title_starting_soon_named, locationName)
            mins <= 5 -> context.getString(R.string.nowcast_notif_title_starting_soon)
            hasLocation -> context.getString(R.string.nowcast_notif_title_starting_in_named, mins, locationName)
            else -> context.getString(R.string.nowcast_notif_title_starting_in, mins)
        }
        val intensity = context.getString(
            when {
                t.peakMm >= 2.5 -> R.string.nowcast_notif_intensity_heavy
                t.peakMm >= 1.0 -> R.string.nowcast_notif_intensity_steady
                else -> R.string.nowcast_notif_intensity_light
            }
        )
        val body = if (hasLocation) {
            context.getString(R.string.nowcast_notif_body_starting_named, intensity, locationName, mins)
        } else {
            context.getString(R.string.nowcast_notif_body_starting, intensity, mins)
        }
        title to body
    }
    is NowcastTransition.RainStopping -> {
        val mins = t.minutesUntil.coerceAtLeast(1)
        val title = if (mins <= 5) {
            context.getString(R.string.nowcast_notif_title_stopping_soon)
        } else {
            context.getString(R.string.nowcast_notif_title_easing_in, mins)
        }
        title to context.getString(R.string.nowcast_notif_body_stopping, mins)
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

    /** Drop a future-dated timestamp left behind by a device-clock rollback. */
    fun clearCooldown() {
        prefs.edit().putLong(KEY_TIMESTAMP, 0L).apply()
    }

    companion object {
        private const val KEY_SIGNATURE = "last_signature"
        private const val KEY_TIMESTAMP = "last_notified_at"
    }
}
