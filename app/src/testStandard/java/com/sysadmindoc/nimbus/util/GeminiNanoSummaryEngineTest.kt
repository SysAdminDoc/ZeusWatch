package com.sysadmindoc.nimbus.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the standard-flavor [GeminiNanoSummaryEngine].
 *
 * Two surfaces are exercised here:
 *  1. The static [GeminiNanoSummaryEngine.buildPrompt] helper, which is
 *     the actual contract the model receives. Its shape determines the
 *     summary quality, so its formatting is the highest-leverage thing
 *     to lock down with assertions.
 *  2. The fallback paths of an engine constructed in an environment
 *     where the ML Kit GenAI runtime is not available; that's exactly what
 *     a JVM unit test sees, and exactly what an unsupported phone sees
 *     (Pixel <8, most Samsung devices). The engine must degrade cleanly
 *     to "unavailable / generate returns null" without crashing.
 *
 * Live `generateContent` happy-path coverage isn't here on purpose;
 * the [com.google.mlkit.genai.prompt.GenerativeModel] class isn't openly
 * mockable, and the [WeatherSummaryEngine] tests already cover the
 * delegate-and-fallback wiring through the [SummaryEngine] interface.
 */
class GeminiNanoSummaryEngineTest {

    // ── buildPrompt formatting ──────────────────────────────────────────

    @Test
    fun `buildPrompt includes the standard preamble and full weather context`() {
        val prompt = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "72°F",
            condition = "Partly Cloudy",
            high = "80°F",
            low = "65°F",
            humidity = 55,
            windSpeed = "8 mph",
            precipChance = 0,
            uvIndex = 6.0,
        )
        assertTrue(
            "prompt should start with the 1-2 sentence directive",
            prompt.startsWith("Write a brief, friendly 1-2 sentence weather summary for: "),
        )
        assertTrue("prompt should include current temp + condition", prompt.contains("Currently 72°F, Partly Cloudy."))
        assertTrue("prompt should include high and low", prompt.contains("High 80°F, low 65°F."))
        assertTrue("prompt should include wind", prompt.contains("Wind 8 mph."))
        assertTrue("prompt should include UV index", prompt.contains("UV index 6."))
        assertTrue("prompt should include humidity", prompt.contains("Humidity 55%."))
    }

    @Test
    fun `buildPrompt omits the rain phrase when precipChance is zero`() {
        val prompt = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "72°F", condition = "Clear", high = "80°F", low = "65°F",
            humidity = 50, windSpeed = "5 mph", precipChance = 0, uvIndex = 4.0,
        )
        assertFalse("rain phrase should not appear when precipChance is zero", prompt.contains("chance of rain"))
    }

    @Test
    fun `buildPrompt includes the rain phrase when precipChance is positive`() {
        val prompt = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "72°F", condition = "Cloudy", high = "75°F", low = "65°F",
            humidity = 80, windSpeed = "12 mph", precipChance = 60, uvIndex = 2.0,
        )
        assertTrue("rain phrase should appear when precipChance > 0", prompt.contains("60% chance of rain."))
        // And it must come AFTER the high/low block, BEFORE the wind block
        // — that ordering is what gives the model a coherent context flow.
        val rainIdx = prompt.indexOf("60% chance of rain.")
        val highIdx = prompt.indexOf("High 75°F, low 65°F.")
        val windIdx = prompt.indexOf("Wind 12 mph.")
        assertTrue("rain after high/low", rainIdx > highIdx)
        assertTrue("rain before wind", rainIdx < windIdx)
    }

    @Test
    fun `buildPrompt rounds uvIndex via toInt for natural language`() {
        // 7.8 → "UV index 7", 0.3 → "UV index 0". The truncation is intentional —
        // a fractional UV is meaningless to a casual reader.
        val high = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "85°F", condition = "Sunny", high = "90°F", low = "70°F",
            humidity = 30, windSpeed = "3 mph", precipChance = 0, uvIndex = 7.8,
        )
        assertTrue("UV index should truncate to 7", high.contains("UV index 7."))
        val low = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "60°F", condition = "Cloudy", high = "65°F", low = "55°F",
            humidity = 70, windSpeed = "4 mph", precipChance = 10, uvIndex = 0.3,
        )
        assertTrue("UV index should truncate to 0", low.contains("UV index 0."))
    }

    @Test
    fun `buildPrompt preserves unit symbols passed by caller`() {
        // The engine never converts units itself; it formats whatever the caller
        // already converted. This guards against a regression where some refactor
        // accidentally strips the unit suffix.
        val celsius = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "22°C", condition = "Clear", high = "27°C", low = "18°C",
            humidity = 50, windSpeed = "10 km/h", precipChance = 0, uvIndex = 3.0,
        )
        assertTrue(celsius.contains("Currently 22°C"))
        assertTrue(celsius.contains("Wind 10 km/h"))
        val imperial = GeminiNanoSummaryEngine.buildPrompt(
            currentTemp = "72°F", condition = "Clear", high = "80°F", low = "65°F",
            humidity = 50, windSpeed = "6 mph", precipChance = 0, uvIndex = 3.0,
        )
        assertTrue(imperial.contains("Currently 72°F"))
        assertTrue(imperial.contains("Wind 6 mph"))
    }

    // ── Engine lifecycle fallbacks ─────────────────────────────────────

    @Test
    fun `engine reports unavailable when GenerativeModel cannot initialize`() {
        // The ML Kit GenAI runtime isn't present in the unit test JVM. The
        // engine must degrade to "unavailable" without crashing; that's exactly
        // the path users on unsupported devices take.
        val engine = GeminiNanoSummaryEngine()
        assertFalse("engine must report unavailable when model init fails", engine.isAvailable())
    }

    @Test
    fun `generate returns null when the engine is unavailable`() = runTest {
        val engine = GeminiNanoSummaryEngine()
        val result = engine.generate(
            currentTemp = "72°F", condition = "Clear", high = "80°F", low = "65°F",
            humidity = 50, windSpeed = "8 mph", precipChance = 0, uvIndex = 6.0,
        )
        assertNull("generate must return null when no model is available", result)
    }

    @Test
    fun `generate returns null after close even if a model would have been available`() = runTest {
        val engine = GeminiNanoSummaryEngine()
        engine.close()
        val result = engine.generate(
            currentTemp = "72°F", condition = "Clear", high = "80°F", low = "65°F",
            humidity = 50, windSpeed = "8 mph", precipChance = 0, uvIndex = 6.0,
        )
        assertNull("generate must return null after close()", result)
        assertFalse("isAvailable must return false after close()", engine.isAvailable())
    }

    @Test
    fun `close is idempotent and never throws`() {
        val engine = GeminiNanoSummaryEngine()
        engine.close()
        engine.close()
        engine.close()
        assertFalse(engine.isAvailable())
    }

    @Test
    fun `engine implements SummaryEngine interface`() {
        // Type-system check: a regression here means the Hilt binding swap
        // between standard- and freenet-flavor SummaryEngine implementations
        // would silently break injection. Keep the contract explicit.
        val engine: SummaryEngine = GeminiNanoSummaryEngine()
        assertEquals(false, engine.isAvailable())
    }
}
