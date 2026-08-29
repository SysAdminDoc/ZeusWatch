package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A free-text on-device model will invent a plausible temperature or rain
 * chance the forecast never contained. These lock down the rule that keeps
 * one off the screen: every number in the summary has to trace back to the
 * forecast the model was handed, or the whole draft is thrown away and the
 * template summary is shown instead.
 */
class WeatherSummaryValidatorTest {

    private val facts = SummaryFacts.from(
        currentTemp = "72°F",
        high = "78°F",
        low = "61°F",
        humidity = 55,
        windSpeed = "9 mph NW",
        precipChance = 20,
        uvIndex = 6.4,
    )

    @Test
    fun `formatted display values are parsed back into forecast numbers`() {
        assertEquals(72, facts.currentTemp)
        assertEquals(78, facts.high)
        assertEquals(61, facts.low)
        assertEquals(9, facts.windSpeed)
        assertEquals(6, facts.uvIndex)
    }

    @Test
    fun `negative temperatures survive parsing`() {
        val cold = SummaryFacts.from("-5°C", "-1°C", "-9°C", 80, "12 km/h", 60, 0.0)

        assertEquals(-5, cold.currentTemp)
        assertEquals(-9, cold.low)
    }

    @Test
    fun `a grounded summary is accepted and joined`() {
        val draft = WeatherSummaryDraft(
            headline = "It is 72 out and pleasant.",
            detail = "Expect a high of 78 with a 20% chance of rain.",
            statedTemperature = 72,
            statedPrecipChance = 20,
        )

        val result = WeatherSummaryValidator.validate(draft, facts)

        assertEquals(
            "It is 72 out and pleasant. Expect a high of 78 with a 20% chance of rain.",
            result.getOrThrow(),
        )
    }

    @Test
    fun `a temperature the forecast never contained is rejected`() {
        val draft = WeatherSummaryDraft(
            headline = "A scorching 95 degrees out there.",
            statedTemperature = 72,
            statedPrecipChance = 20,
        )

        // The structured claim is honest; the prose is not. The prose is what
        // the user reads, so scanning it is the check that matters.
        val reason = rejectionOf(draft)

        assertEquals(SummaryRejection.UnsupportedNumber(95), reason)
    }

    @Test
    fun `a rain claim that disagrees with the forecast is rejected`() {
        val draft = WeatherSummaryDraft(
            headline = "Clear skies today.",
            statedTemperature = 72,
            statedPrecipChance = 80,
        )

        assertEquals(
            SummaryRejection.ClaimMismatch("statedPrecipChance", 80, 20),
            rejectionOf(draft),
        )
    }

    @Test
    fun `a stated temperature that disagrees with the forecast is rejected`() {
        val draft = WeatherSummaryDraft(
            headline = "Mild and clear.",
            statedTemperature = 60,
            statedPrecipChance = 20,
        )

        assertEquals(
            SummaryRejection.ClaimMismatch("statedTemperature", 60, 72),
            rejectionOf(draft),
        )
    }

    @Test
    fun `one degree of rounding drift is tolerated`() {
        // Display temperatures are already rounded, so a model repeating what
        // it read can land a degree away without being wrong.
        val draft = WeatherSummaryDraft(
            headline = "Around 73 right now.",
            statedTemperature = 71,
            statedPrecipChance = 20,
        )

        assertEquals("Around 73 right now.", WeatherSummaryValidator.validate(draft, facts).getOrThrow())
    }

    @Test
    fun `percentages and UV must match exactly`() {
        val humidityOff = WeatherSummaryDraft(
            headline = "Humidity is sitting at 56%.",
            statedTemperature = 72,
            statedPrecipChance = 20,
        )

        assertEquals(SummaryRejection.UnsupportedNumber(56), rejectionOf(humidityOff))
    }

    @Test
    fun `every supported forecast number may appear in the prose`() {
        val draft = WeatherSummaryDraft(
            headline = "72 now, 78 high, 61 low.",
            detail = "Humidity 55, wind 9, UV 6, rain 20.",
            statedTemperature = 72,
            statedPrecipChance = 20,
        )

        assertTrue(WeatherSummaryValidator.validate(draft, facts).isSuccess)
    }

    @Test
    fun `a blank headline is rejected`() {
        val draft = WeatherSummaryDraft(
            headline = "   ",
            detail = "It is 72 out.",
            statedTemperature = 72,
            statedPrecipChance = 20,
        )

        assertEquals(SummaryRejection.BlankHeadline, rejectionOf(draft))
    }

    @Test
    fun `a runaway summary is rejected`() {
        val draft = WeatherSummaryDraft(
            headline = "It is 72 out. ".repeat(30),
            statedTemperature = 72,
            statedPrecipChance = 20,
        )

        assertEquals(SummaryRejection.TooLong, rejectionOf(draft))
    }

    @Test
    fun `an unparseable temperature string does not block validation`() {
        // Some locales format a missing reading as an em dash; the validator
        // must still accept prose that quotes the numbers it does have.
        val partial = SummaryFacts.from("—", "78°F", "61°F", 55, "9 mph", 20, 6.4)
        val draft = WeatherSummaryDraft(
            headline = "High of 78 today.",
            statedTemperature = 0,
            statedPrecipChance = 20,
        )

        assertTrue(WeatherSummaryValidator.validate(draft, partial).isSuccess)
    }

    private fun rejectionOf(draft: WeatherSummaryDraft): SummaryRejection {
        val error = WeatherSummaryValidator.validate(draft, facts).exceptionOrNull()
        return (error as SummaryRejectedException).reason
    }
}
