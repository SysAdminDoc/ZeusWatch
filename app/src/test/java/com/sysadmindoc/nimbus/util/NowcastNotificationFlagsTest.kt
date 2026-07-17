package com.sysadmindoc.nimbus.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the nowcast countdown silence contract: countdown re-posts of the SAME
 * transition (same signature, refreshed minutes every ~5 min) must set
 * `FLAG_ONLY_ALERT_ONCE` so re-notifying the stable nowcast id does not replay
 * sound/vibration up to ~11 times per rain event — while a genuinely NEW
 * transition must still alert audibly. The ambient group summary is re-posted
 * on every child delivery and must therefore always be alert-once.
 *
 * Uses a plain [android.app.Application] to bypass `NimbusApplication`'s
 * Hilt / WorkManager startup, which these builder-level tests do not need.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NowcastNotificationFlagsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    private fun onlyAlertOnce(notification: Notification): Boolean =
        notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0

    @Test
    fun `countdown update posts silently via only-alert-once`() {
        val notification = AlertNotificationHelper.bigTextNowcastNotification(
            context = context,
            title = "Rain in 8 min",
            body = "Light rain expected in 8 minutes.",
            pendingIntent = pendingIntent(),
            silentUpdate = true,
        )

        assertTrue(onlyAlertOnce(notification))
    }

    @Test
    fun `new transition still alerts audibly`() {
        val notification = AlertNotificationHelper.bigTextNowcastNotification(
            context = context,
            title = "Rain in 25 min",
            body = "Steady rain expected in 25 minutes.",
            pendingIntent = pendingIntent(),
            silentUpdate = false,
        )

        assertFalse(onlyAlertOnce(notification))
    }

    @Test
    fun `silent flag defaults to audible`() {
        val notification = AlertNotificationHelper.bigTextNowcastNotification(
            context = context,
            title = "Rain in 25 min",
            body = "Steady rain expected in 25 minutes.",
            pendingIntent = pendingIntent(),
        )

        assertFalse(onlyAlertOnce(notification))
    }

    @Test
    fun `ambient group summary never re-alerts on re-post`() {
        val summary = AlertNotificationHelper.ambientSummary(
            context = context,
            channelId = AlertNotificationHelper.CHANNEL_NOWCAST,
        )

        assertTrue(onlyAlertOnce(summary))
    }
}
