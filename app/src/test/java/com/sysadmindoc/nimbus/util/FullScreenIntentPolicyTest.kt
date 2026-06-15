package com.sysadmindoc.nimbus.util

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenIntentPolicyTest {

    private val context = mockk<Context>(relaxed = true)
    private val notificationManager = mockk<NotificationManager>()

    init {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
    }

    @Test
    fun `full-screen intent allowed below API 34`() {
        assertTrue(AlertNotificationHelper.isFullScreenIntentAllowed(context, sdkInt = 33))
    }

    @Test
    fun `full-screen intent allowed on API 34 when system grants permission`() {
        every { notificationManager.canUseFullScreenIntent() } returns true
        assertTrue(AlertNotificationHelper.isFullScreenIntentAllowed(context, sdkInt = 34))
    }

    @Test
    fun `full-screen intent denied on API 34 when system revokes permission`() {
        every { notificationManager.canUseFullScreenIntent() } returns false
        assertFalse(AlertNotificationHelper.isFullScreenIntentAllowed(context, sdkInt = 34))
    }

    @Test
    fun `full-screen intent denied on API 35 when permission revoked`() {
        every { notificationManager.canUseFullScreenIntent() } returns false
        assertFalse(AlertNotificationHelper.isFullScreenIntentAllowed(context, sdkInt = 35))
    }
}
