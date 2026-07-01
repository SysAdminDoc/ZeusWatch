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
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.repository.LocationRepository
import com.sysadmindoc.nimbus.data.repository.SourceOverrides
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.data.repository.WeatherSourceManager
import com.sysadmindoc.nimbus.data.repository.sourceOverrides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import java.util.Locale

private const val TAG = "AlertCheckWorker"

/**
 * Periodic background worker that checks for new severe weather alerts.
 *
 * Improvements over naive approach:
 * - Deduplicates: tracks seen alert IDs so the same alert is never re-notified.
 * - Multi-location: optionally checks all saved locations, not just last GPS.
 * - Expired filtering: skips alerts whose `expires` timestamp is in the past.
 * - Respects user preferences: early-exits if notifications disabled; filters by min severity.
 */
@HiltWorker
class AlertCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val weatherSourceManager: WeatherSourceManager,
    private val locationRepository: LocationRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()

        // Respect user preference
        if (!settings.alertNotificationsEnabled) {
            Log.d(TAG, "Alert notifications disabled, skipping")
            return Result.success()
        }

        val seenIds = prefs.getSeenAlertIds()
        val maxSortOrder = settings.alertMinSeverity.maxSortOrder
        val newSeenIds = mutableSetOf<String>()

        // Determine locations to check
        val locations = if (settings.alertCheckAllLocations) {
            distinctAlertCheckLocations(
                locationRepository.getAll().map {
                    AlertCheckLocation(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        name = it.name,
                        countryHint = it.country.ifBlank { null },
                        sourceOverrides = it.sourceOverrides(),
                    )
                }
            )
        } else {
            val alertLoc = prefs.backgroundAlertLocation.first()
            if (alertLoc != null) {
                listOf(
                    AlertCheckLocation(
                        latitude = alertLoc.latitude,
                        longitude = alertLoc.longitude,
                        name = alertLoc.name,
                    )
                )
            }
            else emptyList()
        }

        if (locations.isEmpty()) {
            Log.d(TAG, "No locations to check")
            return Result.success()
        }

        var anyUntrustworthy = false
        for (location in locations) {
            val result = weatherSourceManager.getAlertsDetailed(
                location.latitude,
                location.longitude,
                location.sourceOverrides,
                includeMeteredSources = false,
                countryHint = location.countryHint,
            )
            val alerts = result.alerts

            // A result is trustworthy as "all clear" only if no provider was down.
            // If every provider failed, OR a provider failed and we got zero
            // alerts, we cannot distinguish an outage from clear skies — retry
            // rather than risk silently dropping a severe-weather alert.
            val untrustworthy = result.allAdaptersFailed ||
                (result.failedSources.isNotEmpty() && alerts.isEmpty())
            if (untrustworthy) {
                anyUntrustworthy = true
            }
            if (result.failedSources.isNotEmpty()) {
                Log.w(TAG, "Location=${location.name}: alert sources failed: ${result.failedSources}")
            }

            val filtered = alerts.filter { alert ->
                // Fail open on UNKNOWN severity: a provider that doesn't map its
                // severity field cleanly must not have its alerts silently
                // suppressed by the user's min-severity filter — an unmapped
                // tornado warning is worse than an extra notification.
                (alert.severity.sortOrder <= maxSortOrder || alert.severity == AlertSeverity.UNKNOWN) &&
                    alert.id !in seenIds &&
                    alert.id !in newSeenIds &&
                    !isExpired(alert)
            }

            Log.d(TAG, "Location=${location.name}: ${alerts.size} total, ${filtered.size} new matching alerts")

            val showLocation = settings.alertCheckAllLocations && locations.size > 1
            for (alert in filtered) {
                val delivered = AlertNotificationHelper.showAlertNotification(
                    context = applicationContext,
                    alert = alert,
                    locationName = if (showLocation) location.name else null,
                )
                if (delivered) {
                    newSeenIds.add(alert.id)
                }
            }
        }

        if (newSeenIds.isNotEmpty()) {
            prefs.addSeenAlertIds(newSeenIds)
        }

        // If any monitored location couldn't be trusted as "all clear" (every
        // provider down, or a provider down with no alerts returned), let
        // WorkManager retry with the configured exponential backoff (bounded by
        // runAttemptCount) instead of waiting a full 15-minute period — a
        // transient outage must not delay or mask a severe-weather alert.
        return if (anyUntrustworthy && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun isExpired(alert: WeatherAlert): Boolean {
        return isAlertExpired(alert.expires)
    }

    companion object {
        private const val WORK_NAME = "nimbus_alert_check"
        // Cap retries per run so a sustained outage can't spin the worker; after
        // this it falls through to the next periodic tick.
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
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

internal fun distinctAlertCheckLocations(
    locations: List<AlertCheckLocation>,
): List<AlertCheckLocation> {
    val seen = linkedSetOf<String>()
    return locations.filter { location ->
        seen.add("${location.latitude.alertLocationKey()}:${location.longitude.alertLocationKey()}")
    }
}

internal data class AlertCheckLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val countryHint: String? = null,
    val sourceOverrides: SourceOverrides = SourceOverrides(),
)

private fun Double.alertLocationKey(): String = "%.4f".format(Locale.US, this)
