package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DailyBriefingWorkerTest {

    @Test
    fun initialDelayUsesTodayWhenBriefingTimeIsStillAhead() {
        val delay = initialDelayUntilDailyBriefing(
            now = ZonedDateTime.of(2026, 6, 11, 7, 30, 0, 0, newYork),
            minutesAfterMidnight = 8 * 60,
        )

        assertEquals(Duration.ofMinutes(30), delay)
    }

    @Test
    fun initialDelayRollsToTomorrowWhenBriefingTimePassed() {
        val delay = initialDelayUntilDailyBriefing(
            now = ZonedDateTime.of(2026, 6, 11, 8, 1, 0, 0, newYork),
            minutesAfterMidnight = 8 * 60,
        )

        assertEquals(Duration.ofHours(23).plusMinutes(59), delay)
    }

    @Test
    fun initialDelayClampsOutOfRangeMinutes() {
        val delay = initialDelayUntilDailyBriefing(
            now = ZonedDateTime.of(2026, 6, 11, 23, 58, 0, 0, newYork),
            minutesAfterMidnight = 24 * 60 + 10,
        )

        assertEquals(Duration.ofMinutes(1), delay)
    }

    @Test
    fun initialDelayCountsTheHourTheClocksGoBack() {
        // 20:00 EDT to 07:00 EST is 12 hours of real time, not 11.
        val delay = initialDelayUntilDailyBriefing(
            now = ZonedDateTime.of(2026, 10, 31, 20, 0, 0, 0, newYork),
            minutesAfterMidnight = 7 * 60,
        )

        assertEquals(Duration.ofHours(12), delay)
    }

    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun nextBriefingIsTomorrowWhenTheRunLandsExactlyOnTime() {
        // The pin is set from inside the 07:00 run itself, so 07:00 must
        // already count as past or the next run would be this one again.
        val next = nextDailyBriefingAt(ZonedDateTime.of(2026, 6, 11, 7, 0, 0, 0, newYork), 7 * 60)

        assertEquals(ZonedDateTime.of(2026, 6, 12, 7, 0, 0, 0, newYork), next)
    }

    @Test
    fun nextBriefingKeepsTheWallClockTimeAcrossTheFallChange() {
        // 2026-11-01 02:00 EDT falls back to 01:00 EST: that night is 25 hours long.
        val next = nextDailyBriefingAt(ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, newYork), 7 * 60)

        assertEquals(LocalDateTime.of(2026, 11, 1, 7, 0), next.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-5), next.offset)
    }

    @Test
    fun nextBriefingInTheSpringGapMovesForward() {
        // 2026-03-08 02:00 EST jumps to 03:00 EDT, so 02:30 never happens that day.
        val next = nextDailyBriefingAt(ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, newYork), 2 * 60 + 30)

        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), next.toLocalDateTime())
    }

    @Test
    fun aLateNightBriefingHeldPastMidnightIsStillYesterdays() {
        // Otherwise tonight's 23:30 would find "today" already sent and post nothing.
        val slot = dailyBriefingSlotDate(ZonedDateTime.of(2026, 6, 12, 0, 15, 0, 0, newYork), 23 * 60 + 30)

        assertEquals(LocalDate.of(2026, 6, 11), slot)
    }

    @Test
    fun aBriefingOnOrAfterItsTimeIsTodays() {
        assertEquals(
            LocalDate.of(2026, 6, 11),
            dailyBriefingSlotDate(ZonedDateTime.of(2026, 6, 11, 7, 0, 0, 0, newYork), 7 * 60),
        )
        assertEquals(
            LocalDate.of(2026, 6, 11),
            dailyBriefingSlotDate(ZonedDateTime.of(2026, 6, 11, 9, 40, 0, 0, newYork), 7 * 60),
        )
    }

    @Test
    fun aBriefingMovedForwardBySpringGapIsStillTodays() {
        // 02:30 doesn't exist on 2026-03-08, so the run lands at 03:30.
        val slot = dailyBriefingSlotDate(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork), 2 * 60 + 30)

        assertEquals(LocalDate.of(2026, 3, 8), slot)
    }

    @Test
    fun aBriefingInTheRepeatedHourAfterItsTimeIsStillTodays() {
        // 2026-11-01 runs 01:00-02:00 twice. The 01:30 pin is the first
        // 01:30 (EDT), so a run at the second 01:10 (EST) came after it.
        val minutes = 60 + 30
        val afterPin = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 11, 1, 1, 10), newYork, ZoneOffset.ofHours(-5))
        val beforePin = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 11, 1, 1, 10), newYork, ZoneOffset.ofHours(-4))

        assertEquals(LocalDate.of(2026, 11, 1), dailyBriefingSlotDate(afterPin, minutes))
        assertEquals(LocalDate.of(2026, 10, 31), dailyBriefingSlotDate(beforePin, minutes))
    }

    @Test
    fun theClockHasMovedOnlyWhenTheOffsetDiffers() {
        val inNewYork = ZonedDateTime.of(2026, 6, 11, 7, 0, 0, 0, newYork)

        assertFalse("no pin yet", clockMovedSincePin(null, inNewYork))
        assertFalse("same zone", clockMovedSincePin("America/New_York", inNewYork))
        assertFalse("another name for the same clock", clockMovedSincePin("America/Toronto", inNewYork))
        assertTrue("an hour west", clockMovedSincePin("America/Chicago", inNewYork))
        assertTrue("a zone this device doesn't know", clockMovedSincePin("Mars/Olympus_Mons", inNewYork))
    }

    @Test
    fun thePinnedClockFallsBackToTheCurrentOneWhenThereIsNoKnownZone() {
        val inNewYork = ZonedDateTime.of(2026, 6, 11, 7, 0, 0, 0, newYork)

        assertEquals(newYork, pinnedClock(null, inNewYork))
        assertEquals(ZoneId.of("America/Los_Angeles"), pinnedClock("America/Los_Angeles", inNewYork))
        assertEquals(newYork, pinnedClock("Mars/Olympus_Mons", inNewYork))
    }
}
