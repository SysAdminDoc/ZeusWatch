package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AlertTimeTest {

    @Test
    fun `parseAlertInstant accepts offset timestamps`() {
        assertEquals(
            Instant.parse("2026-06-15T16:00:00Z"),
            parseAlertInstant("2026-06-15T10:00:00-06:00"),
        )
    }

    @Test
    fun `parseAlertInstant accepts local timestamps with T or space separator`() {
        val zone = ZoneId.of("America/Chicago")

        assertEquals(
            Instant.parse("2026-06-15T15:00:00Z"),
            parseAlertInstant("2026-06-15T10:00:00", zone),
        )
        assertEquals(
            Instant.parse("2026-06-15T15:00:00Z"),
            parseAlertInstant("2026-06-15 10:00:00", zone),
        )
    }

    @Test
    fun `parseAlertInstant rejects malformed timestamps`() {
        assertNull(parseAlertInstant("not a timestamp"))
    }

    @Test
    fun `isAlertExpired compares parsed instants`() {
        val now = Instant.parse("2026-06-15T16:00:00Z")

        assertTrue(isAlertExpired("2026-06-15T15:59:00Z", now))
        assertFalse(isAlertExpired("2026-06-15T16:01:00Z", now))
        assertFalse(isAlertExpired("not a timestamp", now))
    }
}
