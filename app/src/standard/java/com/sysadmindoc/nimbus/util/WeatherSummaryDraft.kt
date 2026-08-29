package com.sysadmindoc.nimbus.util

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * The JSON shape Gemini Nano must return for a weather summary.
 *
 * The prose fields must contain no digits at all. Checking numbers *inside*
 * the prose against the forecast cannot work: "it is a warm 78 out right now"
 * quotes a real forecast value (today's high) in the wrong role, and no
 * scanner can tell which fact a loose number was meant to describe. So the
 * model is not allowed to write numbers, and the numeric claims it does make
 * come back in named fields where each can be checked against the fact it
 * names. The card already shows the figures elsewhere.
 */
@Generable(
    description = "A short weather summary in words only, with the numbers reported separately.",
)
// Public, not internal: the KSP schema compiler emits a public
// GenerableProvider for this class, and a public declaration cannot expose an
// internal type argument.
data class WeatherSummaryDraft(
    @Guide(
        description = "One friendly sentence about the weather right now. " +
            "Describe it in words only. Never write a digit.",
    )
    val headline: String = "",
    @Guide(
        description = "One more short sentence about what to expect, in words only. " +
            "Never write a digit. May be empty.",
    )
    val detail: String = "",
    @Guide(
        description = "The current temperature from the request, as a whole number, " +
            "in the same unit the request used.",
        minimum = -150.0,
        maximum = 200.0,
    )
    val statedTemperature: Int = 0,
    @Guide(
        description = "The chance of rain from the request, as a whole percentage.",
        minimum = 0.0,
        maximum = 100.0,
    )
    val statedPrecipChance: Int = 0,
)

/**
 * The forecast numbers the model was given, parsed back out of the display
 * strings so a claim can be compared against exactly what was supplied.
 */
internal data class SummaryFacts(
    val currentTemp: Int?,
    val precipChance: Int,
) {
    companion object {
        private val FIRST_INTEGER = Regex("-?\\p{Nd}+")

        /** First integer in a formatted value: "72°F" -> 72, "-5°C" -> -5. */
        internal fun firstInteger(text: String): Int? =
            FIRST_INTEGER.find(text)?.value?.toIntOrNull()

        fun from(currentTemp: String, precipChance: Int): SummaryFacts = SummaryFacts(
            currentTemp = firstInteger(currentTemp),
            precipChance = precipChance,
        )
    }
}

/** Why a draft was rejected, for logging. Never shown to the user. */
internal sealed interface SummaryRejection {
    data object BlankHeadline : SummaryRejection
    data object TooLong : SummaryRejection

    /** The prose contained a digit, which it is never allowed to do. */
    data class NumberInProse(val text: String) : SummaryRejection

    /** A named claim disagreed with the forecast, or was left unfilled. */
    data class ClaimMismatch(val field: String, val stated: Int?, val actual: Int?) : SummaryRejection
}

/**
 * Accepts a [WeatherSummaryDraft] only when its prose is number-free and every
 * numeric claim it makes matches the forecast.
 *
 * A claim that disagrees is not merely a bad number to drop: it means the
 * model misread the forecast it was handed, so its words are not trustworthy
 * either and the whole draft is thrown away.
 */
internal object WeatherSummaryValidator {

    /** Display temperatures are rounded, so a claim may sit one degree off. */
    private const val TEMPERATURE_TOLERANCE = 1

    /** Two sentences. Anything longer means the model ignored the instruction. */
    private const val MAX_SUMMARY_CHARS = 240

    /**
     * Every Unicode decimal digit, not just ASCII. Kotlin Regex does not set
     * UNICODE_CHARACTER_CLASS, so `\\d` misses Arabic-Indic and fullwidth
     * digits — the ones a localized model is most likely to emit.
     */
    private val ANY_DIGIT = Regex("\\p{Nd}")

    /**
     * Numbers spelled out. A digit ban alone does not stop "a warm
     * seventy-eight out there", which quotes today's high as the current
     * temperature just as misleadingly.
     */
    private val NUMBER_WORD = Regex(
        "\\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|cero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|veinte|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun validate(draft: WeatherSummaryDraft, facts: SummaryFacts): Result<String> {
        val headline = draft.headline.trim()
        if (headline.isBlank()) return reject(SummaryRejection.BlankHeadline)

        val summary = listOf(headline, draft.detail.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (summary.length > MAX_SUMMARY_CHARS) return reject(SummaryRejection.TooLong)
        if (ANY_DIGIT.containsMatchIn(summary) || NUMBER_WORD.containsMatchIn(summary)) {
            return reject(SummaryRejection.NumberInProse(summary))
        }

        // An unfilled claim is a failure, not a pass: leaving the field unset
        // is the easiest way for a model to dodge the check entirely.
        // The schema declares both fields required and range-bounded, so a
        // sentinel for "unstated" is not expressible: the model always sends a
        // number. Every one of them is compared against the fact it names.
        if (draft.statedPrecipChance != facts.precipChance) {
            return reject(
                SummaryRejection.ClaimMismatch(
                    field = "statedPrecipChance",
                    stated = draft.statedPrecipChance,
                    actual = facts.precipChance,
                ),
            )
        }
        if (facts.currentTemp == null ||
            !within(draft.statedTemperature, facts.currentTemp, TEMPERATURE_TOLERANCE)
        ) {
            return reject(
                SummaryRejection.ClaimMismatch(
                    "statedTemperature",
                    draft.statedTemperature,
                    facts.currentTemp,
                ),
            )
        }
        return Result.success(summary)
    }

    private fun within(claim: Int, actual: Int, tolerance: Int): Boolean =
        kotlin.math.abs(claim - actual) <= tolerance

    private fun reject(reason: SummaryRejection): Result<String> =
        Result.failure(SummaryRejectedException(reason))
}

internal class SummaryRejectedException(val reason: SummaryRejection) :
    Exception("Summary rejected: $reason")
