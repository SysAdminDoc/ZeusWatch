package com.sysadmindoc.nimbus.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Mirror of `NetworkModule.REDACT_REGEX` so the regression bite stays fresh
 * without exposing the production regex through reflection.
 */
class ApiKeyRedactionTest {

    private val redact = Regex(
        "([?&](?:appid|apikey|api_key|key)=)[^&\\s]+",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun `strips OWM appid from url`() {
        val line = "--> GET https://api.openweathermap.org/data/3.0/onecall?lat=1&lon=2&appid=SECRETKEY"
        val redacted = redact.replace(line, "$1***")
        assertFalse("must not contain the key", redacted.contains("SECRETKEY"))
        assertEquals(
            "--> GET https://api.openweathermap.org/data/3.0/onecall?lat=1&lon=2&appid=***",
            redacted,
        )
    }

    @Test
    fun `does not rewrite path-embedded keys (Pirate Weather)`() {
        val line = "--> GET https://api.pirateweather.net/forecast/MYKEY/1.0,2.0"
        // Pirate Weather embeds the key in the path; we intentionally only
        // redact query params so this line is unchanged. Documenting the
        // limitation here so future readers don't think the regex is broken.
        val redacted = redact.replace(line, "$1***")
        assertEquals(line, redacted)
    }

    @Test
    fun `strips query apikey parameter`() {
        val line = "--> GET https://example.com?apikey=XYZ123&lang=en"
        val redacted = redact.replace(line, "$1***")
        assertFalse(redacted.contains("XYZ123"))
        assertEquals("--> GET https://example.com?apikey=***&lang=en", redacted)
    }

    @Test
    fun `is case insensitive`() {
        val line = "--> GET https://example.com?APIKEY=XYZ"
        val redacted = redact.replace(line, "$1***")
        assertFalse(redacted.contains("XYZ"))
    }

    @Test
    fun `leaves non-secret params alone`() {
        val line = "--> GET https://api.open-meteo.com/v1/forecast?latitude=40.7&longitude=-74.0"
        val redacted = redact.replace(line, "$1***")
        assertEquals(line, redacted)
    }
}
