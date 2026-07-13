package com.sysadmindoc.nimbus.data.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitInterceptorTest {

    private fun buildChain(
        response: () -> Response,
        recorder: MutableList<Request> = mutableListOf(),
    ): Interceptor.Chain = object : FakeInterceptorChain() {
        private val req = Request.Builder().url("https://example.com/").build()
        override fun request(): Request = req
        override fun proceed(request: Request): Response {
            recorder.add(request)
            return response()
        }
    }

    private fun okResponse() = Response.Builder()
        .request(Request.Builder().url("https://example.com/".toHttpUrl()).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("ok")
        .body(ByteArray(0).toResponseBody(null))
        .build()

    private fun tooManyResponse(retryAfterSeconds: Int?) = Response.Builder()
        .request(Request.Builder().url("https://example.com/".toHttpUrl()).build())
        .protocol(Protocol.HTTP_1_1)
        .code(429)
        .message("rate limited")
        .body(ByteArray(0).toResponseBody(null))
        .apply { if (retryAfterSeconds != null) header("Retry-After", retryAfterSeconds.toString()) }
        .build()

    @Test
    fun `first request is not throttled`() {
        val slept = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 1.0, burst = 1,
            sleep = { slept.add(it) },
            now = { 0L },
        )

        val response = interceptor.intercept(buildChain({ okResponse() }))

        assertEquals(200, response.code)
        assertTrue("first request must not sleep", slept.all { it == 0L })
    }

    @Test
    fun `fails fast when wait exceeds cap`() {
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 0.1, burst = 1, maxWaitMs = 500L,
            sleep = { /* no-op so we see the fail-fast path */ },
            now = { 0L },
        )
        // burst=1 means one "free" slot beyond the initial request at t=0.
        // Drain both so the third request must wait `intervalMs` (10s).
        interceptor.intercept(buildChain({ okResponse() }))
        interceptor.intercept(buildChain({ okResponse() }))

        val response = interceptor.intercept(buildChain({ okResponse() }))

        assertEquals("fail-fast must return 429", 429, response.code)
        assertEquals("true", response.header("X-ZeusWatch-Throttled"))
    }

    @Test
    fun `steady-state accepts at configured rate`() {
        val clock = longArrayOf(0L)
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 1.0, burst = 1,
            sleep = { /* skip wait in test — we advance the clock manually */ },
            now = { clock[0] },
        )
        // Burn both starting slots.
        interceptor.intercept(buildChain({ okResponse() }))
        interceptor.intercept(buildChain({ okResponse() }))

        // Advance the clock past intervalMs; the next request should be accepted.
        clock[0] = 2_500L
        val response = interceptor.intercept(buildChain({ okResponse() }))
        assertEquals(200, response.code)
    }

    @Test
    fun `honours Retry-After on 429`() {
        val slept = mutableListOf<Long>()
        val recorded = mutableListOf<Request>()
        var remaining = 1
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 100.0, burst = 100,
            sleep = { slept.add(it) },
            now = { 0L },
        )

        val response = interceptor.intercept(
            buildChain(
                response = {
                    if (remaining-- > 0) tooManyResponse(retryAfterSeconds = 2) else okResponse()
                },
                recorder = recorded,
            ),
        )

        assertEquals(200, response.code)
        assertEquals("must retry exactly once", 2, recorded.size)
        val retrySleep = slept.firstOrNull { it >= 2_000L }
        assertNotNull("must sleep at least Retry-After seconds", retrySleep)
    }

    @Test
    fun `429 without Retry-After is not retried`() {
        val recorded = mutableListOf<Request>()
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 100.0, burst = 100,
            sleep = { },
            now = { 0L },
        )

        val response = interceptor.intercept(
            buildChain(response = { tooManyResponse(retryAfterSeconds = null) }, recorder = recorded),
        )

        assertEquals(429, response.code)
        assertEquals("must not retry without Retry-After", 1, recorded.size)
        assertNull(response.header("X-ZeusWatch-Throttled"))
    }

    @Test
    fun `Retry-After is capped to prevent worker stall`() {
        val slept = mutableListOf<Long>()
        var remaining = 1
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 100.0, burst = 100,
            maxRetryBackoffMs = 5_000L,
            sleep = { slept.add(it) },
            now = { 0L },
        )

        interceptor.intercept(
            buildChain(
                response = {
                    // Server asks for 5 hours — cap must kick in.
                    if (remaining-- > 0) tooManyResponse(retryAfterSeconds = 18_000) else okResponse()
                },
            ),
        )

        val retrySleep = slept.maxOrNull() ?: -1L
        assertTrue("must cap at maxRetryBackoffMs", retrySleep <= 5_000L)
    }

    @Test
    fun `throttled requests consume slots so the limiter eventually fails fast`() {
        // Regression guard: a request that waits (but under the cap) must still
        // reserve its slot. Previously the wait path returned a sleep without
        // advancing the TAT, so the bucket never drained — every request slept
        // the same amount and proceeded, and the limiter never engaged.
        val slept = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            hostLabel = "test", rate = 1.0, burst = 2, maxWaitMs = 5_000L,
            sleep = { slept.add(it) },
            now = { 0L }, // frozen clock: only slot reservation can advance the TAT
        )

        val codes = (1..12).map { interceptor.intercept(buildChain({ okResponse() })).code }

        // With slots actually consumed, back-pressure escalates and the limiter
        // must fail fast once the burst + wait budget is spent. With the bug,
        // every call returned 200 and this list would contain no 429.
        assertTrue("limiter must eventually 429 once budget is spent", codes.contains(429))
        assertEquals("stays throttled once over budget", 429, codes.last())
        // Waits must escalate (proof the TAT advanced), not repeat a fixed value.
        assertTrue("waits must escalate as slots are reserved", (slept.maxOrNull() ?: 0L) >= 4_000L)
    }
}
