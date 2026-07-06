package com.sysadmindoc.nimbus.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class BlitzortungServiceTest {

    @Test
    fun `blitzortungReconnectDelayMs backs off exponentially with a cap`() {
        assertEquals(1_000L, blitzortungReconnectDelayMs(0))
        assertEquals(2_000L, blitzortungReconnectDelayMs(1))
        assertEquals(4_000L, blitzortungReconnectDelayMs(2))
        assertEquals(32_000L, blitzortungReconnectDelayMs(5))
        assertEquals(32_000L, blitzortungReconnectDelayMs(12))
    }
}
