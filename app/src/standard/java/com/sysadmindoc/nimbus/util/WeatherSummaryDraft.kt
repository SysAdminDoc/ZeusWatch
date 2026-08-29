package com.sysadmindoc.nimbus.util

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * The JSON shape Gemini Nano must return for a weather summary.
 *
 * A free-text model will happily invent a plausible temperature or rain chance
 * that the forecast never contained. Forcing a schema gives us the model's own
 * numeric claims in machine-readable form, so [WeatherSummaryValidator] can
 * check them against the forecast before any of it reaches the screen.
 */
@Generable(
    description = "A short weather summary that uses only the numbers supplied in the prompt.",
)
internal data class WeatherSummaryDraft(
    @Guide(description = "One friendly sentence about the weather right now.")
    val headline: String = "",
    @Guide(description = "One more short sentence about what to expect. May be empty.")
    val detail: String = "",
    @Guide(
        description = "The current temperature you referred to, as a whole number, " +
            "in the same unit the prompt used.",
        minimum = -150.0,
        maximum = 200.0,
    )
    val statedTemperature: Int = 0,
    @Guide(
        description = "The chance of rain you referred to, as a whole percentage.",
        minimum = 0.0,
        maximum = 100.0,
    )
    val statedPrecipChance: Int = 0,
    @Guide(
        description = "Short risk words that apply, such as heat, cold, wind, uv or rain.",
        maxItems = 3,
    )
    val riskFlags: List<String> = emptyList(),
    @Guide(
        description = "How confident this summary is.",
        enumValues = ["high", "medium", "low"],
    )
    val confidence: String = "",
)

/**
 * The forecast numbers the model was given, parsed back out of the display
 * strings so a claim can be compared against exactly what was supplied.
 */
internal data class SummaryFacts(
    val currentTemp: Int?,
    val high: Int?,
    val low: Int?,
    val humidity: Int,
    val windSpeed: Int?,
    val precipChance: Int,
    val uvIndex: Int,
) {
    companion object {
        private val FIRST_INTEGER = Regex("-?\\d+")

        /** First integer in a formatted value: "72°F" -> 72, "18 km/h NW" -> 18. */
        internal fun firstInteger(text: String): Int? =
            FIRST_INTEGER.find(text)?.value?.toIntOrNull()

        fun from(
            currentTemp: String,
            high: String,
            low: String,
            humidity: Int,
            windSpeed: String,
            precipChance: Int,
            uvIndex: Double,
        ): SummaryFacts = SummaryFacts(
            currentTemp = firstInteger(currentTemp),
            high = firstInteger(high),
            low = firstInteger(low),
            humidity = humidity,
            windSpeed = firstInteger(windSpeed),
            precipChance = precipChance,
            uvIndex = uvIndex.toInt(),
        )
    }
}

/** Why a draft was rejected, for logging. Never shown to the user. */
internal sealed interface SummaryRejection {
    data object BlankHeadline : SummaryRejection
    data object TooLong : SummaryRejection
    data class ClaimMismatch(val field: String, val stated: Int, val actual: Int?) : SummaryRejection
    data class UnsupportedNumber(val value: Int) : SummaryRejection
}

/**
 * Accepts a [WeatherSummaryDraft] only when every number in it traces back to
 * the forecast.
 *
 * Both the structured claims and every integer in the prose are checked. The
 * prose scan is the part that matters: a model can report `statedTemperature`
 * correctly and still write "a balmy 30 degrees" in the sentence, and that
 * sentence is what the user reads.
 */
internal object WeatherSummaryValidator {

    /** Display temperatures are rounded, so a claim may sit one degree off. */
    private const val TEMPERATURE_TOLERANCE = 1

    /** Wind speed is rounded the same way. */
    private const val WIND_TOLERANCE = 1

    /** Two sentences. Anything longer means the model ignored the instruction. */
    private const val MAX_SUMMARY_CHARS = 240

    private val INTEGERS = Regex("-?\\d+")

    fun validate(draft: WeatherSummaryDraft, facts: SummaryFacts): Result<String> {
        val headline = draft.headline.trim()
        if (headline.isBlank()) return reject(SummaryRejection.BlankHeadline)

        val summary = listOf(headline, draft.detail.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (summary.length > MAX_SUMMARY_CHARS) return reject(SummaryRejection.TooLong)

        facts.currentTemp?.let { actual ->
            if (!within(draft.statedTemperature, actual, TEMPERATURE_TOLERANCE)) {
                return reject(
                    SummaryRejection.ClaimMismatch("statedTemperature", draft.statedTemperature, actual),
                )
            }
        }
        if (draft.statedPrecipChance != facts.precipChance) {
            return reject(
                SummaryRejection.ClaimMismatch(
                    "statedPrecipChance",
                    draft.statedPrecipChance,
                    facts.precipChance,
                ),
            )
        }

        val supported = supportedNumbers(facts)
        INTEGERS.findAll(summary).forEach { match ->
            val value = match.value.toIntOrNull() ?: return@forEach
            if (supported.none { (allowed, tolerance) -> within(value, allowed, tolerance) }) {
                return reject(SummaryRejection.UnsupportedNumber(value))
            }
        }
        return Result.success(summary)
    }

    /** Each forecast number paired with how far a claim may stray from it. */
    private fun supportedNumbers(facts: SummaryFacts): List<Pair<Int, Int>> = buildList {
        facts.currentTemp?.let { add(it to TEMPERATURE_TOLERANCE) }
        facts.high?.let { add(it to TEMPERATURE_TOLERANCE) }
        facts.low?.let { add(it to TEMPERATURE_TOLERANCE) }
        facts.windSpeed?.let { add(it to WIND_TOLERANCE) }
        add(facts.humidity to 0)
        add(facts.precipChance to 0)
        add(facts.uvIndex to 0)
    }

    private fun within(claim: Int, actual: Int, tolerance: Int): Boolean =
        kotlin.math.abs(claim - actual) <= tolerance

    private fun reject(reason: SummaryRejection): Result<String> =
        Result.failure(SummaryRejectedException(reason))
}

internal class SummaryRejectedException(val reason: SummaryRejection) :
    Exception("Summary rejected: $reason")
