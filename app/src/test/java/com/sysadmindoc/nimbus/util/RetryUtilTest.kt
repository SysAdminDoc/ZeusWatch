package com.sysadmindoc.nimbus.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RetryUtilTest {

    @Test
    fun `returns immediately on success`() = runTest {
        var calls = 0
        val result = withRetry {
            calls++
            Result.success("ok")
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(1, calls)
    }

    @Test
    fun `retries on IOException and succeeds`() = runTest {
        var calls = 0
        val result = withRetry(initialDelayMs = 1L) {
            calls++
            if (calls < 3) Result.failure(IOException("network down"))
            else Result.success("recovered")
        }
        assertEquals("recovered", result.getOrNull())
        assertEquals(3, calls)
    }

    @Test
    fun `retries on 5xx HttpException`() = runTest {
        var calls = 0
        val result = withRetry(initialDelayMs = 1L) {
            calls++
            if (calls < 2) Result.failure(
                HttpException(Response.error<String>(502, okhttp3.ResponseBody.create(null, "")))
            )
            else Result.success("ok")
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(2, calls)
    }

    @Test
    fun `does not retry on 4xx HttpException`() = runTest {
        var calls = 0
        val result = withRetry(initialDelayMs = 1L) {
            calls++
            Result.failure<String>(
                HttpException(Response.error<String>(404, okhttp3.ResponseBody.create(null, "")))
            )
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `does not retry on non-retryable exception`() = runTest {
        var calls = 0
        val result = withRetry(initialDelayMs = 1L) {
            calls++
            Result.failure<String>(IllegalArgumentException("bad input"))
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `exhausts all attempts and returns last failure`() = runTest {
        var calls = 0
        val result = withRetry(maxAttempts = 3, initialDelayMs = 1L) {
            calls++
            Result.failure<String>(IOException("still failing"))
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(3, calls)
    }

    @Test
    fun `respects maxAttempts parameter`() = runTest {
        var calls = 0
        val result = withRetry(maxAttempts = 2, initialDelayMs = 1L) {
            calls++
            Result.failure<String>(IOException("fail"))
        }
        assertTrue(result.isFailure)
        assertEquals(2, calls)
    }
}
