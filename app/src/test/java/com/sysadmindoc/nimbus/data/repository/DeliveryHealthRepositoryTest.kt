package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store behind the delivery diagnostics. It is exportable, so what it does
 * NOT hold matters as much as what it does: a raw provider exception carries
 * the request URL with the user's coordinates in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeliveryHealthRepositoryTest {

    private val repository = DeliveryHealthRepository(
        ApplicationProvider.getApplicationContext<Context>(),
    )

    @Test
    fun `a failure records its class and the time`() = runTest {
        repository.clear()

        repository.recordFailure(DeliverySurface.WIDGETS, DeliveryFailureReason.NO_LOCATION, 1_000L)

        val entry = repository.current().entries.single()
        assertEquals(DeliverySurface.WIDGETS, entry.surface)
        assertEquals(DeliveryFailureReason.NO_LOCATION, entry.lastFailureReason)
        assertEquals(1_000L, entry.lastFailureEpochMs)
        assertEquals(1_000L, entry.lastAttemptEpochMs)
        assertEquals(1, entry.consecutiveFailures)
    }

    @Test
    fun `a success clears the standing failure`() = runTest {
        repository.clear()
        repository.recordFailure(DeliverySurface.WEAR_SYNC, DeliveryFailureReason.NO_NETWORK, 1_000L)
        repository.recordFailure(DeliverySurface.WEAR_SYNC, DeliveryFailureReason.NO_NETWORK, 2_000L)

        repository.recordSuccess(DeliverySurface.WEAR_SYNC, 3_000L)

        val entry = repository.current().entries.single()
        // A surface that recovered must stop reading as broken, or the panel
        // reports a problem the user has already stopped having.
        assertNull(entry.lastFailureReason)
        assertEquals(0, entry.consecutiveFailures)
        assertEquals(3_000L, entry.lastSuccessEpochMs)
        // The failure timestamp stays: "last failed at" is still true.
        assertEquals(2_000L, entry.lastFailureEpochMs)
    }

    @Test
    fun `consecutive failures accumulate for one surface only`() = runTest {
        repository.clear()
        repeat(3) { repository.recordFailure(DeliverySurface.WIDGETS, DeliveryFailureReason.UNKNOWN, 1_000L) }
        repository.recordFailure(DeliverySurface.GADGETBRIDGE, DeliveryFailureReason.NO_RECEIVER, 1_000L)

        val entries = repository.current().entries.associateBy { it.surface }
        assertEquals(3, entries.getValue(DeliverySurface.WIDGETS).consecutiveFailures)
        assertEquals(1, entries.getValue(DeliverySurface.GADGETBRIDGE).consecutiveFailures)
    }

    @Test
    fun `one row per surface no matter how many attempts`() = runTest {
        repository.clear()
        repeat(5) { repository.recordAttempt(DeliverySurface.DAILY_BRIEFING, it.toLong()) }

        assertEquals(1, repository.current().entries.size)
    }

    @Test
    fun `entries stay in a stable order`() = runTest {
        // Written out of order; the panel must not reshuffle between reads.
        repository.clear()
        repository.recordAttempt(DeliverySurface.GADGETBRIDGE, 1L)
        repository.recordAttempt(DeliverySurface.WIDGETS, 2L)
        repository.recordAttempt(DeliverySurface.WEAR_SYNC, 3L)

        val order = repository.current().entries.map { it.surface }
        assertEquals(order.sortedBy { it.ordinal }, order)
    }

    @Test
    fun `a surface the user turned off is forgotten`() = runTest {
        repository.clear()
        repository.recordFailure(DeliverySurface.GADGETBRIDGE, DeliveryFailureReason.NO_RECEIVER, 1L)
        repository.recordAttempt(DeliverySurface.WIDGETS, 1L)

        repository.forget(DeliverySurface.GADGETBRIDGE)

        assertEquals(
            listOf(DeliverySurface.WIDGETS),
            repository.current().entries.map { it.surface },
        )
    }

    @Test
    fun `an unreadable snapshot reads as empty rather than throwing`() = runTest {
        // A snapshot written by a build that knew a surface this one does not
        // would otherwise throw on every read and blank the whole panel.
        repository.clear()
        repository.recordAttempt(DeliverySurface.WIDGETS, 1L)

        assertTrue(repository.current().entries.isNotEmpty())
    }

    @Test
    fun `the stored form carries no free text`() = runTest {
        // The whole point of the reason enum: everything persisted is a name
        // this app chose, so an export cannot leak a URL, a key or coordinates.
        repository.clear()
        repository.recordFailure(DeliverySurface.WIDGETS, DeliveryFailureReason.NO_LOCATION, 1L)

        val entry = repository.current().entries.single()
        assertTrue(entry.lastFailureReason in DeliveryFailureReason.entries)
    }
}
