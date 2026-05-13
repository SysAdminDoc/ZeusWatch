package com.sysadmindoc.nimbus.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Mirror of `NetworkModule.REDACT_REGEX` so the regression bite stays fresh
 * without exposing the production regex through reflection.
 */
class ApiKeyRedactionTest {

    private val queryRedact = Regex(
        "([?&](?:appid|apikey|api_key|key)=)[^&\\s]+",
        RegexOption.IGNORE_CASE,
    )

    private val pathRedact = Regex(
        "(/forecast/)[^/\\s?#]+(?=/-?\\d)",
        RegexOption.IGNORE_CASE,
    )

    private fun redact(line: String): String =
        pathRedact.replace(queryRedact.replace(line, "$1***"), "$1***")

    @Test
    fun `strips OWM appid from url`() {
        val line = "--> GET https://api.openweathermap.org/data/3.0/onecall?lat=1&lon=2&appid=SECRETKEY"
        val redacted = redact(line)
        assertFalse("must not contain the key", redacted.contains("SECRETKEY"))
        assertEquals(
            "--> GET https://api.openweathermap.org/data/3.0/onecall?lat=1&lon=2&appid=***",
            redacted,
        )
    }

    @Test
    fun `strips Pirate Weather path-embedded key`() {
        val line = "--> GET https://api.pirateweather.net/forecast/MYKEY/1.0,2.0"
        val redacted = redact(line)
        assertFalse("must not contain the key", redacted.contains("MYKEY"))
        assertEquals(
            "--> GET https://api.pirateweather.net/forecast/***/1.0,2.0",
            redacted,
        )
    }

    @Test
    fun `redacts Pirate Weather path key alongside exclude suffix`() {
        // Pirate Weather lets callers append `,exclude=…` to the key segment.
        val line = "--> GET https://api.pirateweather.net/forecast/MYKEY,exclude=minutely/-14.0,170.5"
        val redacted = redact(line)
        assertFalse("must not contain the key", redacted.contains("MYKEY"))
        assertEquals(
            "--> GET https://api.pirateweather.net/forecast/***/-14.0,170.5",
            redacted,
        )
    }

    @Test
    fun `strips query apikey parameter`() {
        val line = "--> GET https://example.com?apikey=XYZ123&lang=en"
        val redacted = redact(line)
        assertFalse(redacted.contains("XYZ123"))
        assertEquals("--> GET https://example.com?apikey=***&lang=en", redacted)
    }

    @Test
    fun `is case insensitive`() {
        val line = "--> GET https://example.com?APIKEY=XYZ"
        val redacted = redact(line)
        assertFalse(redacted.contains("XYZ"))
    }

    @Test
    fun `leaves non-secret params alone`() {
        val line = "--> GET https://api.open-meteo.com/v1/forecast?latitude=40.7&longitude=-74.0"
        val redacted = redact(line)
        assertEquals(line, redacted)
    }
}
