package com.sysadmindoc.nimbus.ui.screen.customalerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAlertsScreenLogicTest {

    @Test
    fun `threshold input in progress covers normal transitional values`() {
        assertTrue(isThresholdInputInProgress(""))
        assertTrue(isThresholdInputInProgress("-"))
        assertTrue(isThresholdInputInProgress("."))
        assertTrue(isThresholdInputInProgress("-."))
        assertTrue(isThresholdInputInProgress("12."))
    }

    @Test
    fun `threshold input in progress rejects complete values`() {
        assertFalse(isThresholdInputInProgress("0"))
        assertFalse(isThresholdInputInProgress("-5"))
        assertFalse(isThresholdInputInProgress("12.5"))
    }
}
