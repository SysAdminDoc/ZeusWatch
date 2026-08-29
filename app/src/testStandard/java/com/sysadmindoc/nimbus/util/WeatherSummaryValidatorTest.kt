package com.sysadmindoc.nimbus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An on-device model will happily write a plausible number the forecast never
 * contained. An earlier version of this validator scanned the prose and
 * accepted any number that matched *some* forecast value, which let a model
 * quote today's high as the current temperature and pass. The rule is now
 * simpler and actually holds: the prose carries no digits at all, and each
 * numeric claim is checked against the one fact it names.
 */
class WeatherSummaryValidatorTest {

    private val facts = SummaryFacts.from(currentTemp = "72°F", precipChance = 20)

    @Test
    fun `the current temperature is parsed out of the display string`() {
        assertEquals(72, facts.currentTemp)
        assertEquals(-5, SummaryFacts.from("-5°C", 0).currentTemp)
        assertEquals(null, SummaryFacts.from("—", 0).currentTemp)
    }

    @Test
    fun `a number-free summary with matching claims is accepted`() {
        val draft = draft(
            headline = "Mild and clear right now.",
            detail = "A slim chance of showers later on.",
        )

        assertEquals(
            "Mild and clear right now. A slim chance of showers later on.",
            WeatherSummaryValidator.validate(draft, facts).getOrThrow(),
        )
    }

    @Test
    fun `a real forecast value quoted in the wrong role is rejected`() {
        // 78 is a genuine forecast number (today's high) used as the current
        // temperature. The old pooled prose scan accepted this.
        val draft = draft(headline = "It is a warm 78 out right now.")

        assertTrue(rejectionOf(draft) is SummaryRejection.NumberInProse)
    }

    @Test
    fun `a rain chance invented in the prose is rejected`() {
        // statedPrecipChance is honest here; the sentence is not. The sentence
        // is what the user reads.
        val draft = draft(
            headline = "Clear for now.",
            detail = "Rain chance climbs to 61% this evening.",
        )

        assertTrue(rejectionOf(draft) is SummaryRejection.NumberInProse)
    }

    @Test
    fun `any digit anywhere in the prose is rejected`() {
        listOf(
            "Temps run 61-78 today.",
            "Winds pick up after 3pm.",
            "A 20% chance of rain.",
            "Clear skies. Highs near 78.",
        ).forEach { text ->
            assertTrue("$text should be rejected", rejectionOf(draft(headline = text)) is SummaryRejection.NumberInProse)
        }
    }

    @Test
    fun `non-ASCII digits are rejected too`() {
        // Kotlin Regex does not enable UNICODE_CHARACTER_CLASS, so a bare
        // \d misses these entirely and a localized model walks straight
        // through the digit ban.
        listOf(
            "It is a warm ٦٨ out right now.",
            "It is a warm ７８ out right now.",
        ).forEach { text ->
            assertTrue("$text should be rejected", rejectionOf(draft(headline = text)) is SummaryRejection.NumberInProse)
        }
    }

    @Test
    fun `numbers spelled out in words are rejected`() {
        // "a warm seventy-eight" quotes today's high as the current
        // temperature just as misleadingly as the digits would.
        listOf(
            "It is a warm seventy-eight out right now.",
            "Rain chance climbs to sixty percent this evening.",
            "Unos treinta grados ahora mismo.",
        ).forEach { text ->
            assertTrue("$text should be rejected", rejectionOf(draft(headline = text)) is SummaryRejection.NumberInProse)
        }
    }

    @Test
    fun `ordinary weather words are not mistaken for numbers`() {
        // The word list must not swallow normal phrasing.
        listOf(
            "Clear and mild with a light breeze.",
            "Cloudy later, turning breezy overnight.",
            "Despejado y templado esta tarde.",
        ).forEach { text ->
            assertTrue("$text should be accepted", WeatherSummaryValidator.validate(draft(headline = text), facts).isSuccess)
        }
    }

    @Test
    fun `a stated temperature that disagrees with the forecast is rejected`() {
        val draft = draft(headline = "Mild and clear.", statedTemperature = 60)

        assertEquals(
            SummaryRejection.ClaimMismatch("statedTemperature", 60, 72),
            rejectionOf(draft),
        )
    }

    @Test
    fun `a stated rain chance that disagrees with the forecast is rejected`() {
        val draft = draft(headline = "Clear skies today.", statedPrecipChance = 80)

        assertEquals(
            SummaryRejection.ClaimMismatch("statedPrecipChance", 80, 20),
            rejectionOf(draft),
        )
    }

    @Test
    fun `a zero rain chance is a real value, not a missing one`() {
        val dry = SummaryFacts.from(currentTemp = "72°F", precipChance = 0)
        val draft = draft(headline = "Dry and clear.", statedPrecipChance = 0)

        assertTrue(WeatherSummaryValidator.validate(draft, dry).isSuccess)
    }

    @Test
    fun `an unreadable current temperature rejects rather than skipping the check`() {
        // Some locales render a missing reading as an em dash. With no fact to
        // compare against, a temperature claim cannot be trusted at all.
        val unknown = SummaryFacts.from(currentTemp = "—", precipChance = 20)

        assertEquals(
            SummaryRejection.ClaimMismatch("statedTemperature", 72, null),
            rejectionOf(draft(headline = "Mild and clear."), unknown),
        )
    }

    @Test
    fun `one degree of rounding drift is tolerated`() {
        // Display temperatures are already rounded, so a model repeating what
        // it read can land a degree away without being wrong.
        assertTrue(WeatherSummaryValidator.validate(draft(statedTemperature = 71), facts).isSuccess)
        assertTrue(WeatherSummaryValidator.validate(draft(statedTemperature = 73), facts).isSuccess)
        assertTrue(WeatherSummaryValidator.validate(draft(statedTemperature = 74), facts).isFailure)
    }

    @Test
    fun `a blank headline is rejected`() {
        assertEquals(
            SummaryRejection.BlankHeadline,
            rejectionOf(draft(headline = "   ", detail = "It is mild out.")),
        )
    }

    @Test
    fun `a runaway summary is rejected`() {
        assertEquals(
            SummaryRejection.TooLong,
            rejectionOf(draft(headline = "It is mild out. ".repeat(30))),
        )
    }

    private fun draft(
        headline: String = "Mild and clear right now.",
        detail: String = "",
        statedTemperature: Int = 72,
        statedPrecipChance: Int = 20,
    ) = WeatherSummaryDraft(
        headline = headline,
        detail = detail,
        statedTemperature = statedTemperature,
        statedPrecipChance = statedPrecipChance,
    )

    private fun rejectionOf(
        draft: WeatherSummaryDraft,
        against: SummaryFacts = facts,
    ): SummaryRejection {
        val error = WeatherSummaryValidator.validate(draft, against).exceptionOrNull()
        return (error as SummaryRejectedException).reason
    }
}
