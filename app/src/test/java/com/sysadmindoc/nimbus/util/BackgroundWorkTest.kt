package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.sysadmindoc.nimbus.widget.WidgetRefreshWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Which runs wait for a charged battery and which carry the user's mark.
 *
 * A Run now request that picked up the battery hold would sit queued with the
 * panel's button doing nothing, and one without the mark would be held for
 * Wi-Fi like a scheduled run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackgroundWorkTest {

    @Test
    fun `scheduled runs need a network and a battery that isn't low`() {
        val constraints = BackgroundWork.periodicConstraints()
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `severe alert checks keep running on a low battery`() {
        val constraints = BackgroundWork.alertConstraints()
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `the user's mark reads back and its absence means scheduled`() {
        assertTrue(BackgroundWork.isUserInitiated(BackgroundWork.userInitiatedInput()))
        assertFalse(BackgroundWork.isUserInitiated(androidx.work.Data.EMPTY))
    }

    @Test
    fun `every Run now skips the battery hold and the gated ones carry the mark`() {
        val gated: Map<String, OneTimeWorkRequest> = mapOf(
            "daily briefing" to DailyBriefingWorker.oneTimeRequest(),
            "nowcast" to NowcastAlertWorker.oneTimeRequest(),
            "health" to HealthAlertWorker.oneTimeRequest(),
            "custom" to CustomAlertWorker.oneTimeRequest(),
        )
        val all = gated + ("severe alerts" to AlertCheckWorker.oneTimeRequest()) +
            ("widgets" to WidgetRefreshWorker.oneTimeRequest())

        for ((name, request) in all) {
            val constraints = request.workSpec.constraints
            assertEquals(name, NetworkType.CONNECTED, constraints.requiredNetworkType)
            assertFalse(name, constraints.requiresBatteryNotLow())
        }
        for ((name, request) in gated) {
            assertTrue(name, BackgroundWork.isUserInitiated(request.workSpec.input))
        }
        // The widget worker reads its own key for the same mark.
        assertTrue(
            WidgetRefreshWorker.oneTimeRequest().workSpec.input
                .getBoolean(WidgetRefreshWorker.KEY_FORCE_REFRESH, false),
        )
    }

    /**
     * What each schedule() actually hands WorkManager. The constraint helpers
     * above are only half of it: a schedule site that builds its own
     * Constraints, or picks the wrong helper, passes every test above.
     */
    @Test
    fun `every scheduled worker waits out a low battery except the severe alert check`() {
        val periodic = mutableMapOf<String, Pair<ExistingPeriodicWorkPolicy, PeriodicWorkRequest>>()
        withRecordingWorkManager(
            onPeriodic = { name, policy, request -> periodic[name] = policy to request },
        ) {
            DailyBriefingWorker.schedule(context, minutesAfterMidnight = 7 * 60)
            NowcastAlertWorker.schedule(context)
            HealthAlertWorker.schedule(context)
            CustomAlertWorker.schedule(context)
            WidgetRefreshWorker.schedule(context)
            DatabaseMaintenanceWorker.schedule(context)
            AlertCheckWorker.schedule(context)
        }

        val waitsForBattery = setOf(
            "nimbus_daily_briefing",
            "nimbus_nowcast_alert",
            "nimbus_health_alert",
            "nimbus_custom_alert",
            "nimbus_widget_refresh",
            "nimbus_db_maintenance",
        )
        assertEquals(waitsForBattery + "nimbus_alert_check", periodic.keys)
        for ((name, scheduled) in periodic) {
            val (policy, request) = scheduled
            // UPDATE, so installs scheduled before a constraint changed pick it up.
            assertEquals(name, ExistingPeriodicWorkPolicy.UPDATE, policy)
            assertEquals(name, name in waitsForBattery, request.workSpec.constraints.requiresBatteryNotLow())
        }
        // The cleanup touches only the local database.
        assertEquals(
            NetworkType.NOT_REQUIRED,
            periodic.getValue("nimbus_db_maintenance").second.workSpec.constraints.requiredNetworkType,
        )
        for (name in periodic.keys - "nimbus_db_maintenance") {
            assertEquals(name, NetworkType.CONNECTED, periodic.getValue(name).second.workSpec.constraints.requiredNetworkType)
        }
    }

    @Test
    fun `a widget tap skips the battery hold, carries the mark and never cancels a running refresh`() {
        val oneTime = mutableMapOf<String, Pair<ExistingWorkPolicy, OneTimeWorkRequest>>()
        withRecordingWorkManager(
            onOneTime = { name, policy, request -> oneTime[name] = policy to request },
        ) {
            WidgetRefreshWorker.enqueueImmediate(context)
        }

        val (policy, request) = oneTime.getValue("nimbus_widget_refresh_manual_refresh")
        assertEquals(ExistingWorkPolicy.KEEP, policy)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
        assertTrue(request.workSpec.input.getBoolean(WidgetRefreshWorker.KEY_FORCE_REFRESH, false))
    }

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun withRecordingWorkManager(
        onPeriodic: (String, ExistingPeriodicWorkPolicy, PeriodicWorkRequest) -> Unit = { _, _, _ -> },
        onOneTime: (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit = { _, _, _ -> },
        block: () -> Unit,
    ) {
        val workManager = mockk<WorkManager>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } answers {
            onPeriodic(firstArg(), secondArg(), thirdArg())
            mockk<Operation>(relaxed = true)
        }
        every { workManager.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) } answers {
            onOneTime(firstArg(), secondArg(), thirdArg())
            mockk<Operation>(relaxed = true)
        }
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any<Context>()) } returns workManager
            block()
        } finally {
            unmockkObject(WorkManager.Companion)
        }
    }
}
