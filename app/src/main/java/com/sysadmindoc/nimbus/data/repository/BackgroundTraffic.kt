package com.sysadmindoc.nimbus.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Requests made and bytes moved, headers included, by one background surface. */
data class TrafficSample(val requests: Int, val bytes: Long) {
    val isEmpty: Boolean get() = requests == 0 && bytes == 0L

    operator fun plus(other: TrafficSample) = TrafficSample(requests + other.requests, bytes + other.bytes)

    companion object {
        val NONE = TrafficSample(0, 0L)
    }
}

/**
 * Counts the network traffic each background surface causes.
 *
 * A worker runs its body inside [meter], which puts the surface in a
 * thread-local for every thread the coroutine runs on. OkHttp asks
 * [eventListenerFactory] for a listener inside `newCall`, on the calling
 * thread (Retrofit's suspend calls make the raw call there too, before they
 * suspend), so the listener is born knowing its surface and counts the call
 * when it ends, on whichever thread OkHttp finishes it. Calls made while the
 * app is in front get no listener and aren't counted.
 *
 * Bytes are what crossed the socket for each request and response, headers
 * included, before gzip is undone. TLS framing isn't in it.
 */
object BackgroundTraffic {
    private val current = ThreadLocal<DeliverySurface?>()
    private val requests = ConcurrentHashMap<DeliverySurface, AtomicLong>()
    private val bytes = ConcurrentHashMap<DeliverySurface, AtomicLong>()

    /** Set on the base OkHttp client; every provider client is built from it. */
    val eventListenerFactory: EventListener.Factory = EventListener.Factory {
        current.get()?.let(::CountingListener) ?: EventListener.NONE
    }

    /**
     * Runs [block] with its traffic counted against [surface], then adds what it
     * used to that surface's hourly totals in [deliveryHealth]. The write runs
     * even when the work is cancelled, so a stopped run still shows its cost.
     */
    suspend fun <T> meter(
        surface: DeliverySurface,
        deliveryHealth: DeliveryHealthRepository,
        block: suspend () -> T,
    ): T = withContext(current.asContextElement(surface)) {
        try {
            block()
        } finally {
            withContext(NonCancellable) {
                deliveryHealth.recordTraffic(surface, drain(surface), System.currentTimeMillis())
            }
        }
    }

    /** Takes what [surface] has used since the last drain. */
    fun drain(surface: DeliverySurface): TrafficSample = TrafficSample(
        requests = requests[surface]?.getAndSet(0L)?.toInt() ?: 0,
        bytes = bytes[surface]?.getAndSet(0L) ?: 0L,
    )

    /** Forgets every count; for tests, which share this one process-wide meter. */
    internal fun drainAll() = DeliverySurface.entries.forEach { drain(it) }

    private fun add(surface: DeliverySurface, byteCount: Long) {
        requests.getOrPut(surface) { AtomicLong() }.incrementAndGet()
        bytes.getOrPut(surface) { AtomicLong() }.addAndGet(byteCount)
    }

    /** One per call. Its events can arrive on more than one thread, hence the atomics. */
    private class CountingListener(private val surface: DeliverySurface) : EventListener() {
        private val bytes = AtomicLong()
        private val done = AtomicBoolean()

        override fun requestHeadersEnd(call: Call, request: Request) {
            bytes.addAndGet(request.headers.byteCount())
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            bytes.addAndGet(byteCount)
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            bytes.addAndGet(response.headers.byteCount())
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            bytes.addAndGet(byteCount)
        }

        override fun callEnd(call: Call) = finish()

        override fun callFailed(call: Call, ioe: IOException) = finish()

        private fun finish() {
            if (done.compareAndSet(false, true)) add(surface, bytes.get())
        }
    }
}
