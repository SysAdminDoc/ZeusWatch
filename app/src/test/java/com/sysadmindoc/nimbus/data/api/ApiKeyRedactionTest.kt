package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.di.redactPirateWeatherPathKey
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Query-param redaction is provided by OkHttp 5. The app keeps only the
 * Pirate Weather path-key scrubber because those credentials are not query
 * parameters.
 */
class ApiKeyRedactionTest {

    @Test
    fun `OkHttp logger strips OWM appid from url`() {
        val messages = mutableListOf<String>()
        val interceptor = HttpLoggingInterceptor { message -> messages += message }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactQueryParams("appid", "apikey", "api_key", "key")
        }
        val request = Request.Builder()
            .url("https://api.openweathermap.org/data/3.0/onecall?lat=1&lon=2&appid=SECRETKEY")
            .build()

        interceptor.intercept(StaticResponseChain(request))

        val joined = messages.joinToString("\n")
        assertFalse("must not contain the key", joined.contains("SECRETKEY"))
        assertTrue("must keep non-secret query params", joined.contains("lat=1"))
        assertTrue("must preserve the redacted key name", joined.contains("appid="))
    }

    @Test
    fun `strips Pirate Weather path-embedded key`() {
        val line = "--> GET https://api.pirateweather.net/forecast/MYKEY/1.0,2.0"
        val redacted = redactPirateWeatherPathKey(line)
        assertFalse("must not contain the key", redacted.contains("MYKEY"))
        assertEquals(
            "--> GET https://api.pirateweather.net/forecast/***/1.0,2.0",
            redacted,
        )
    }

    @Test
    fun `redacts Pirate Weather path key alongside exclude suffix`() {
        val line = "--> GET https://api.pirateweather.net/forecast/MYKEY,exclude=minutely/-14.0,170.5"
        val redacted = redactPirateWeatherPathKey(line)
        assertFalse("must not contain the key", redacted.contains("MYKEY"))
        assertEquals(
            "--> GET https://api.pirateweather.net/forecast/***/-14.0,170.5",
            redacted,
        )
    }

    @Test
    fun `redacts Pirate Weather key regardless of coordinate format`() {
        val line = "--> GET https://api.pirateweather.net/forecast/MYKEY/%2B14.0,170.5"
        val redacted = redactPirateWeatherPathKey(line)
        assertFalse("must not contain the key", redacted.contains("MYKEY"))
        assertEquals(
            "--> GET https://api.pirateweather.net/forecast/***/%2B14.0,170.5",
            redacted,
        )
    }

    @Test
    fun `leaves unrelated forecast paths on other hosts untouched`() {
        val line = "--> GET https://api.weather.gov/gridpoints/BOU/63,62/forecast/hourly"
        assertEquals(line, redactPirateWeatherPathKey(line))
    }

    @Test
    fun `path redactor leaves non-secret query params alone`() {
        val line = "--> GET https://api.open-meteo.com/v1/forecast?latitude=40.7&longitude=-74.0"
        val redacted = redactPirateWeatherPathKey(line)
        assertEquals(line, redacted)
    }

    private class StaticResponseChain(
        private val request: Request,
    ) : FakeInterceptorChain() {
        override fun request(): Request = request

        override fun proceed(request: Request): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .body("".toResponseBody())
                .build()
    }
}
