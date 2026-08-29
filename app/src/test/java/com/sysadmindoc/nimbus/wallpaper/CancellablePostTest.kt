package com.sysadmindoc.nimbus.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The wallpaper engine's hand-off from its preference-reading thread back to
 * the main thread. Opening and closing the wallpaper preview quickly used to
 * leave that post queued against a destroyed engine.
 */
class CancellablePostTest {

    @Test
    fun `work runs when nothing cancelled it`() {
        val post = CancellablePost()
        var ran = 0
        val queue = mutableListOf<Runnable>()

        post.submit(work = { ran++ }, post = { queue += it })
        queue.forEach { it.run() }

        assertEquals(1, ran)
    }

    @Test
    fun `cancelling before the work is submitted stops it being posted`() {
        val post = CancellablePost()
        var posted = 0

        post.cancel()
        post.submit(work = {}, post = { posted++ })

        assertEquals(0, posted)
        assertTrue(post.isCancelled)
    }

    @Test
    fun `cancelling hands the queued runnable back to be removed`() {
        val post = CancellablePost()
        val queue = mutableListOf<Runnable>()
        post.submit(work = {}, post = { queue += it })

        post.cancel(remove = { queue.remove(it) })

        assertTrue(queue.isEmpty())
    }

    @Test
    fun `an already queued runnable does nothing after cancellation`() {
        // The window the first version of this fix missed: holding the
        // runnable so it can be removed does not help if the removal happens
        // while the post is already on its way to the handler.
        val post = CancellablePost()
        var ran = 0
        val queue = mutableListOf<Runnable>()
        post.submit(work = { ran++ }, post = { queue += it })

        // Cancel without removing, the way a handler that has already dequeued
        // the message behaves.
        post.cancel()
        queue.forEach { it.run() }

        assertEquals(0, ran)
    }

    @Test
    fun `cancelling twice is harmless`() {
        val post = CancellablePost()
        post.submit(work = {}, post = {})

        post.cancel()
        post.cancel()

        assertTrue(post.isCancelled)
    }

    @Test
    fun `a cancel racing the submit still stops the work`() {
        // The actual shape of the bug: the producing thread assigns then posts
        // while the main thread destroys the engine. Run it enough times to
        // interleave; the work must never run after a cancel.
        repeat(500) {
            val post = CancellablePost()
            val ran = AtomicInteger()
            val queue = java.util.Collections.synchronizedList(mutableListOf<Runnable>())
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)

            Thread {
                start.await()
                post.submit(work = { ran.incrementAndGet() }, post = { queue += it })
                done.countDown()
            }.start()
            Thread {
                start.await()
                post.cancel(remove = { queue.remove(it) })
                done.countDown()
            }.start()

            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            // Whatever survived the race is delivered, as a handler would.
            synchronized(queue) { queue.toList() }.forEach { it.run() }

            assertEquals("work ran after cancel on iteration $it", 0, ran.get())
        }
    }

    @Test
    fun `submitting without cancelling never drops the work`() {
        // The other direction: a nervous implementation that cancelled too
        // eagerly would leave the wallpaper on its default frame forever.
        repeat(200) {
            val post = CancellablePost()
            var ran = 0
            val queue = mutableListOf<Runnable>()

            post.submit(work = { ran++ }, post = { queue += it })
            queue.forEach { it.run() }

            assertEquals(1, ran)
            assertFalse(post.isCancelled)
        }
    }
}
