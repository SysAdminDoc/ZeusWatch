package com.sysadmindoc.nimbus.util

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/**
 * Retries a suspend [block] that returns [Result] on transient failures
 * (network errors and server 5xx) with exponential backoff using
 * coroutine-friendly [delay] instead of blocking the thread.
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1_000L,
    block: suspend () -> Result<T>,
): Result<T> {
    if (maxAttempts < 1) {
        return Result.failure(IllegalArgumentException("maxAttempts must be at least 1"))
    }
    var lastResult: Result<T>? = null
    repeat(maxAttempts) { attempt ->
        val result = block()
        if (result.isSuccess) return result
        lastResult = result
        if (!isRetryable(result.exceptionOrNull())) return result
        if (attempt < maxAttempts - 1) {
            delay(initialDelayMs * (1 shl attempt))
        }
    }
    return lastResult ?: Result.failure(IllegalStateException("retry block did not run"))
}

private fun isRetryable(exception: Throwable?): Boolean = when (exception) {
    is IOException -> true
    is HttpException -> exception.code() in 500..599
    else -> false
}
