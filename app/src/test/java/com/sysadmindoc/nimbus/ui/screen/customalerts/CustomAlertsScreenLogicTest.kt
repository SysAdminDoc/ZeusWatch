package com.sysadmindoc.nimbus.ui.screen.customalerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `threshold parsing accepts both decimal separators`() {
        // Pre-filled values are formatted in the default locale, so comma
        // decimals must parse the same as dots.
        assertEquals(12.5, parseThresholdInput("12.5")!!, 0.0)
        assertEquals(12.5, parseThresholdInput("12,5")!!, 0.0)
        assertEquals(-3.0, parseThresholdInput("-3,0")!!, 0.0)
    }

    @Test
    fun `threshold parsing rejects garbage`() {
        assertNull(parseThresholdInput(""))
        assertNull(parseThresholdInput("abc"))
        assertNull(parseThresholdInput("1,2,3"))
    }
}
