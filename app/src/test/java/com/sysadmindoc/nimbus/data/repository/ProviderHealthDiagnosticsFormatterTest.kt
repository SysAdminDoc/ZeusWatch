package com.sysadmindoc.nimbus.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

class ProviderHealthDiagnosticsFormatterTest {

    @Test
    fun `failure classifier redacts raw failures to reason classes`() {
        assertEquals(ProviderFailureReason.TIMEOUT, ProviderFailureReason.classify(SocketTimeoutException("api host")))
        assertEquals(ProviderFailureReason.HTTP_429, ProviderFailureReason.classify(httpException(429)))
        assertEquals(ProviderFailureReason.HTTP_5XX, ProviderFailureReason.classify(httpException(503)))
        assertEquals(ProviderFailureReason.NETWORK, ProviderFailureReason.classify(UnknownHostException("secret host")))
        assertEquals(ProviderFailureReason.NETWORK, ProviderFailureReason.classify(IOException("network down")))
        assertEquals(ProviderFailureReason.UNSUPPORTED, ProviderFailureReason.classify(UnsupportedOperationException("nope")))
        assertEquals(ProviderFailureReason.UNKNOWN, ProviderFailureReason.classify(IllegalStateException("raw text")))
    }

    @Test
    fun `diagnostics export includes provider health without sensitive context`() {
        val snapshot = ProviderHealthSnapshot(
            entries = listOf(
                ProviderHealthEntry(
                    type = WeatherDataType.FORECAST,
                    provider = WeatherSourceProvider.OPEN_METEO_BOM,
                    lastSuccessEpochMs = 1_771_000_000_000L,
                    lastFailureEpochMs = 1_770_999_000_000L,
                    lastFailureReason = ProviderFailureReason.HTTP_429,
                    lastCacheAgeMinutes = 12,
                    activeFallback = true,
                    fallbackFromProvider = WeatherSourceProvider.OPEN_METEO,
                ),
            ),
        )

        val text = ProviderHealthDiagnosticsFormatter.format(snapshot, nowEpochMs = 1_771_000_100_000L)

        assertTrue(text.contains("ZeusWatch source health diagnostics"))
        assertTrue(text.contains("FORECAST / Open-Meteo + BOM ACCESS-G"))
        assertTrue(text.contains("Failure class: HTTP_429"))
        assertTrue(text.contains("Last cache age: 12 min"))
        assertTrue(text.contains("Active fallback: yes, from Open-Meteo"))
        assertFalse(text.contains("40.0"))
        assertFalse(text.contains("api-key"))
        assertFalse(text.contains("raw text"))
    }
}
