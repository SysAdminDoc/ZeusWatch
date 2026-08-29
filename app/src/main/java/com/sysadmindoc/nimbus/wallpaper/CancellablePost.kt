package com.sysadmindoc.nimbus.wallpaper

/**
 * A single piece of work handed from a background thread to the main thread,
 * which can be cancelled by whoever owns it.
 *
 * The wallpaper engine reads its first preference off the render thread and
 * posts the result back. Opening and closing the wallpaper preview quickly left
 * that post queued against an engine that no longer existed.
 *
 * Holding the runnable so it can be removed from the handler is not enough on
 * its own: the producing thread assigns it and then posts it, and a cancel
 * landing between those two steps removes nothing and the post still runs. So
 * cancellation is a flag the work itself checks, and removing it from the
 * handler is only an optimisation on top.
 */
class CancellablePost {

    // Written on the producing thread, read on the main thread.
    @Volatile
    private var cancelled = false

    @Volatile
    private var pending: Runnable? = null

    val isCancelled: Boolean get() = cancelled

    /**
     * Wraps [work] so it does nothing once [cancel] has been called, and hands
     * the wrapper to [post] unless cancellation already happened.
     */
    fun submit(work: () -> Unit, post: (Runnable) -> Unit) {
        val runnable = Runnable { if (!cancelled) work() }
        pending = runnable
        if (cancelled) return
        post(runnable)
    }

    /**
     * Stops the work from running and hands the queued runnable to [remove] so
     * it can be taken off the handler. Safe to call more than once, and safe to
     * call before [submit].
     */
    fun cancel(remove: (Runnable) -> Unit = {}) {
        cancelled = true
        pending?.let(remove)
        pending = null
    }
}
