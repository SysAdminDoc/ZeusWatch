package com.sysadmindoc.nimbus.data.api

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Per-host rate limiter + 429-aware OkHttp interceptor.
 *
 * Two guardrails:
 *   1. **Client-side token bucket** — prevents ZeusWatch from blowing through
 *      free-tier API quotas (e.g. OWM's 1,000 calls/day, 60 req/min). Blocks
 *      the OkHttp dispatcher thread for up to [maxWaitMs] when the bucket
 *      is empty; any longer and the request fails fast with HTTP 429 so
 *      callers fall back through [WeatherSourceManager] instead of stalling
 *      the UI.
 *   2. **429 / Retry-After handling** — honours the standard `Retry-After`
 *      header (delta-seconds; HTTP-date form intentionally skipped because
 *      none of our providers use it) with a single retry, capped at
 *      [maxRetryBackoffMs] so a misconfigured server can't park a worker.
 *
 * Safe to share across requests — state is a single [AtomicLong] timestamp.
 *
 * @param rate   approximate requests per second the bucket sustains.
 * @param burst  maximum burst allowance before throttling kicks in.
 */
class RateLimitInterceptor(
    private val hostLabel: String,
    private val rate: Double,
    private val burst: Int,
    private val maxWaitMs: Long = 1_500L,
    private val maxRetryBackoffMs: Long = 5_000L,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val now: () -> Long = System::currentTimeMillis,
) : Interceptor {

    init {
        require(rate > 0) { "rate must be positive (host=$hostLabel)" }
        require(burst > 0) { "burst must be positive (host=$hostLabel)" }
    }

    private val intervalMs: Long = (1_000.0 / rate).toLong().coerceAtLeast(1L)
    // Next instant the bucket will be fully refilled.
    private val nextSlotAt = AtomicLong(0L)

    override fun intercept(chain: Interceptor.Chain): Response {
        val waitMs = reserveSlot()
        if (waitMs > maxWaitMs) {
            // Don't block the dispatcher for longer than a user is willing
            // to wait — fail fast so the repository can try a fallback.
            return tooManyRequestsResponse(chain, waitMs)
        }
        if (waitMs > 0) sleep(waitMs)

        val response = chain.proceed(chain.request())
        if (response.code != 429) return response

        val retryAfter = parseRetryAfterMs(response.header("Retry-After"))
            ?: return response
        response.close()

        val backoff = min(retryAfter, maxRetryBackoffMs)
        sleep(backoff)
        // Single retry — if the server still returns 429, surface it so the
        // caller (WeatherSourceManager) can fall back to another provider.
        return chain.proceed(chain.request())
    }

    /**
     * GCRA (Generic Cell Rate Algorithm) reservation. `nextSlotAt` is the
     * theoretical arrival time (TAT) of the next conforming request; it may
     * be up to `burst * intervalMs` ahead of `now` before back-pressure
     * kicks in. Returning a positive wait means the caller must sleep that
     * long before proceeding; a wait above [maxWaitMs] becomes a fail-fast
     * 429 in [intercept].
     *
     * @return milliseconds the caller should sleep before proceeding.
     */
    private fun reserveSlot(): Long {
        val burstWindow = burst * intervalMs
        while (true) {
            val current = nextSlotAt.get()
            val nowMs = now()
            // Over-limit — caller would have to wait `current - nowMs - burstWindow`.
            // Don't advance TAT when rejecting, otherwise overflow compounds.
            val overflow = current - nowMs - burstWindow
            if (overflow > 0) return overflow
            val baseline = max(current, nowMs)
            val target = baseline + intervalMs
            if (nextSlotAt.compareAndSet(current, target)) return 0L
        }
    }

    private fun tooManyRequestsResponse(
        chain: Interceptor.Chain,
        waitMs: Long,
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(429)
        .message("Client rate limit: $hostLabel would need ${waitMs}ms")
        .body(ByteArray(0).toResponseBody(null))
        .header("X-ZeusWatch-Throttled", "true")
        .build()

    private fun parseRetryAfterMs(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val seconds = header.trim().toLongOrNull() ?: return null
        if (seconds < 0) return null
        return seconds * 1_000L
    }
}
