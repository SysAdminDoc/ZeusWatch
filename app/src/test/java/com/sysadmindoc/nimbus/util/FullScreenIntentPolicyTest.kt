package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenIntentPolicyTest {

    @Test
    fun `full-screen intent allowed below API 34`() {
        assertTrue(
            AlertNotificationHelper.isFullScreenIntentAllowedForPolicy(
                sdkInt = 33,
                canUseFullScreenIntent = false,
            ),
        )
    }

    @Test
    fun `full-screen intent allowed on API 34 when system grants permission`() {
        assertTrue(
            AlertNotificationHelper.isFullScreenIntentAllowedForPolicy(
                sdkInt = 34,
                canUseFullScreenIntent = true,
            ),
        )
    }

    @Test
    fun `full-screen intent denied on API 34 when system revokes permission`() {
        assertFalse(
            AlertNotificationHelper.isFullScreenIntentAllowedForPolicy(
                sdkInt = 34,
                canUseFullScreenIntent = false,
            ),
        )
    }

    @Test
    fun `full-screen intent denied on API 35 when permission revoked`() {
        assertFalse(
            AlertNotificationHelper.isFullScreenIntentAllowedForPolicy(
                sdkInt = 35,
                canUseFullScreenIntent = false,
            ),
        )
    }
}
