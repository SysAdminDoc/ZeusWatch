package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/**
 * The exported diagnostics are what a user pastes into a bug report, so the
 * thing worth testing hardest is what cannot appear in them.
 */
class DeliveryDiagnosticsTest {

    private val snapshot = DeliveryHealthSnapshot(
        listOf(
            DeliveryHealthEntry(
                surface = DeliverySurface.WIDGETS,
                lastAttemptEpochMs = 1_700_000_000_000L,
                lastSuccessEpochMs = 1_700_000_000_000L,
            ),
            DeliveryHealthEntry(
                surface = DeliverySurface.WEAR_SYNC,
                lastAttemptEpochMs = 1_700_000_100_000L,
                lastFailureEpochMs = 1_700_000_100_000L,
                lastFailureReason = DeliveryFailureReason.NO_NETWORK,
                consecutiveFailures = 3,
            ),
        ),
    )

    @Test
    fun `the export names every recorded surface`() {
        val text = DeliveryHealthDiagnosticsFormatter.format(snapshot)

        assertTrue(text.contains("WIDGETS"))
        assertTrue(text.contains("WEAR_SYNC"))
        assertTrue(text.contains("NO_NETWORK"))
        assertTrue(text.contains("Consecutive failures: 3"))
    }

    @Test
    fun `the export carries nothing but timestamps and this app's own names`() {
        // Only the data section: the fixed header says the file contains no
        // "raw exception text", and matching on its own promise would be a
        // test of the wording rather than of the data.
        val data = DeliveryHealthDiagnosticsFormatter.format(snapshot)
            .lines()
            .dropWhile { !it.startsWith("- ") }
            .joinToString(separator = System.lineSeparator())

        assertTrue("expected the data section to be present", data.contains("WIDGETS"))
        // Whatever else changes, none of this may ever appear: the store holds
        // an enum precisely so a provider's exception text, which routinely
        // contains the request URL with the user's coordinates, cannot reach
        // a file the user shares.
        listOf("http", "://", "lat=", "lon=", "apikey", "api_key", "Exception", "@").forEach {
            assertFalse("export leaked '$it'", data.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `an empty store says so rather than producing a blank file`() {
        val text = DeliveryHealthDiagnosticsFormatter.format(DeliveryHealthSnapshot())

        assertTrue(text.contains("No delivery attempts have been recorded yet."))
    }

    @Test
    fun `the export prefers WorkManager's schedule over the stored one`() {
        // The stored value is a leftover from the last run; WorkManager knows
        // about the backoff that moved it, which is the case being diagnosed.
        val stored = DeliveryHealthSnapshot(
            listOf(
                DeliveryHealthEntry(
                    surface = DeliverySurface.WIDGETS,
                    nextScheduledEpochMs = 1_700_000_000_000L,
                ),
            ),
        )

        val text = DeliveryHealthDiagnosticsFormatter.format(
            stored,
            nextScheduledRuns = mapOf(DeliverySurface.WIDGETS to 1_800_000_000_000L),
        )

        assertTrue(text.contains(java.time.Instant.ofEpochMilli(1_800_000_000_000L).toString()))
        assertFalse(text.contains(java.time.Instant.ofEpochMilli(1_700_000_000_000L).toString()))
    }

    @Test
    fun `a network exception is recorded as a network failure`() {
        assertEquals(
            DeliveryFailureReason.NO_NETWORK,
            UnknownHostException("api.open-meteo.com").deliveryFailureReason(),
        )
    }

    @Test
    fun `a permission failure is not lumped in with everything else`() {
        assertEquals(
            DeliveryFailureReason.PERMISSION_DENIED,
            SecurityException("denied").deliveryFailureReason(),
        )
    }

    @Test
    fun `an unrecognised exception classifies rather than throwing`() {
        assertEquals(
            DeliveryFailureReason.UNKNOWN,
            RuntimeException("something new").deliveryFailureReason(),
        )
    }

    @Test
    fun `classifying never carries the exception's own text`() {
        // The message here is exactly the shape of the thing that must not be
        // persisted: a URL with coordinates in it.
        val reason = RuntimeException(
            "https://api.open-meteo.com/v1/forecast?latitude=39.7&longitude=-104.9",
        ).deliveryFailureReason()

        assertFalse(reason.name.contains("open-meteo"))
        assertTrue(reason in DeliveryFailureReason.entries)
    }
}
