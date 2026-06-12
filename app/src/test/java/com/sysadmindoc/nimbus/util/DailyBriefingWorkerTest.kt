package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class DailyBriefingWorkerTest {

    @Test
    fun initialDelayUsesTodayWhenBriefingTimeIsStillAhead() {
        val delay = initialDelayUntilDailyBriefing(
            now = LocalDateTime.of(2026, 6, 11, 7, 30),
            minutesAfterMidnight = 8 * 60,
        )

        assertEquals(Duration.ofMinutes(30), delay)
    }

    @Test
    fun initialDelayRollsToTomorrowWhenBriefingTimePassed() {
        val delay = initialDelayUntilDailyBriefing(
            now = LocalDateTime.of(2026, 6, 11, 8, 1),
            minutesAfterMidnight = 8 * 60,
        )

        assertEquals(Duration.ofHours(23).plusMinutes(59), delay)
    }

    @Test
    fun initialDelayClampsOutOfRangeMinutes() {
        val delay = initialDelayUntilDailyBriefing(
            now = LocalDateTime.of(2026, 6, 11, 23, 58),
            minutesAfterMidnight = 24 * 60 + 10,
        )

        assertEquals(Duration.ofMinutes(1), delay)
    }
}
