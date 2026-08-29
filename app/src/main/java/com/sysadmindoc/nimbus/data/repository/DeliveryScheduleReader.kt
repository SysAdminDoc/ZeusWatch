package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sysadmindoc.nimbus.util.AlertCheckWorker
import com.sysadmindoc.nimbus.util.CustomAlertWorker
import com.sysadmindoc.nimbus.util.DailyBriefingWorker
import com.sysadmindoc.nimbus.util.HealthAlertWorker
import com.sysadmindoc.nimbus.util.NowcastAlertWorker
import com.sysadmindoc.nimbus.widget.WidgetRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads WorkManager's own schedule, and runs a delivery job on demand.
 *
 * "Next run" is asked of WorkManager rather than derived from the worker's
 * period constant: the two disagree exactly when it matters, because a job
 * that has been deferred or backed off is the one the user is trying to
 * diagnose.
 */
@Singleton
class DeliveryScheduleReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The unique work each surface rides on.
     *
     * Watch sync and the Gadgetbridge broadcast have no worker of their own;
     * they happen inside the widget refresh, so they share its schedule.
     */
    private fun uniqueWorkName(surface: DeliverySurface): String = when (surface) {
        DeliverySurface.WIDGETS,
        DeliverySurface.WEAR_SYNC,
        DeliverySurface.GADGETBRIDGE,
        -> WidgetRefreshWorker.UNIQUE_WORK_NAME
        DeliverySurface.DAILY_BRIEFING -> DailyBriefingWorker.UNIQUE_WORK_NAME
        DeliverySurface.WEATHER_ALERTS -> AlertCheckWorker.UNIQUE_WORK_NAME
        DeliverySurface.CUSTOM_ALERTS -> CustomAlertWorker.UNIQUE_WORK_NAME
        DeliverySurface.NOWCAST_ALERTS -> NowcastAlertWorker.UNIQUE_WORK_NAME
        DeliverySurface.HEALTH_ALERTS -> HealthAlertWorker.UNIQUE_WORK_NAME
    }

    private fun oneTimeRequest(surface: DeliverySurface): OneTimeWorkRequest = when (surface) {
        DeliverySurface.WIDGETS,
        DeliverySurface.WEAR_SYNC,
        DeliverySurface.GADGETBRIDGE,
        -> WidgetRefreshWorker.oneTimeRequest()
        DeliverySurface.DAILY_BRIEFING -> DailyBriefingWorker.oneTimeRequest()
        DeliverySurface.WEATHER_ALERTS -> AlertCheckWorker.oneTimeRequest()
        DeliverySurface.CUSTOM_ALERTS -> CustomAlertWorker.oneTimeRequest()
        DeliverySurface.NOWCAST_ALERTS -> NowcastAlertWorker.oneTimeRequest()
        DeliverySurface.HEALTH_ALERTS -> HealthAlertWorker.oneTimeRequest()
    }

    /** Next scheduled run per surface, or null where nothing is enqueued. */
    suspend fun nextScheduledRuns(): Map<DeliverySurface, Long> {
        val result = mutableMapOf<DeliverySurface, Long>()
        DeliverySurface.entries.forEach { surface ->
            // The Flow overload rather than the ListenableFuture one: awaiting
            // a future would mean pulling in kotlinx-coroutines-guava, and a
            // new runtime dependency has to earn its place on the freenet
            // classpath and in the notices asset.
            val infos = runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow(uniqueWorkName(surface))
                    .first()
            }.getOrNull().orEmpty()
            infos
                .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                .mapNotNull { it.nextScheduleTimeMillis.takeIf { time -> time != Long.MAX_VALUE } }
                .minOrNull()
                ?.let { result[surface] = it }
        }
        return result
    }

    /**
     * Runs a surface's job now, as a one-off beside its periodic schedule.
     *
     * REPLACE rather than KEEP: tapping again after a failure has to actually
     * retry, not silently join the run that is already stuck.
     */
    fun runNow(surface: DeliverySurface) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            // Keyed on the surface, not the worker. Watch sync, the
            // Gadgetbridge broadcast and the widgets share one worker, so a
            // single manual name meant Run now on one row cancelled an
            // in-flight run started from another, which rethrows the
            // cancellation and records nothing at all.
            "manual_" + surface.name.lowercase(),
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest(surface),
        )
    }
}
