package com.sysadmindoc.nimbus.util

import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Finds the best stretch of hours to do something in, and says why.
 *
 * The existing [ActivityIndexEvaluator] scores right now, which answers
 * "should I go out this second" and nothing else. What people plan around is
 * the next good window, so this scores each of the coming hours and reports the
 * longest run of them that is actually worth going out in.
 */
object ActivityWindowEvaluator {

    /** A run of hours has to reach this to count as worth doing. */
    const val GOOD_SCORE = 60

    /** Hours beyond this are too far out to plan an afternoon around. */
    const val HORIZON_HOURS = 24

    fun evaluate(
        hourly: List<HourlyConditions>,
        thresholds: ActivityThresholds = ActivityThresholds(),
        aqiByHour: Map<LocalDateTime, Int> = emptyMap(),
        activities: List<ActivityType> = ActivityType.entries,
    ): List<ActivityWindow> {
        val horizon = hourly.take(HORIZON_HOURS)
        return activities.map { type -> evaluateOne(type, horizon, thresholds, aqiByHour) }
    }

    private fun evaluateOne(
        type: ActivityType,
        horizon: List<HourlyConditions>,
        thresholds: ActivityThresholds,
        aqiByHour: Map<LocalDateTime, Int>,
    ): ActivityWindow {
        if (horizon.isEmpty()) {
            return ActivityWindow(type = type, confidence = ActivityWindowConfidence.NONE)
        }

        val scored = horizon.map { hour ->
            HourScore(
                hour = hour,
                factors = scoreHour(type, hour, thresholds, aqiByHour[hour.time]),
            )
        }

        val runs = mutableListOf<List<HourScore>>()
        var current = mutableListOf<HourScore>()
        scored.forEach { entry ->
            if (entry.score >= GOOD_SCORE) {
                current += entry
            } else if (current.isNotEmpty()) {
                runs += current.toList()
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) runs += current.toList()

        // Longest first, then best mean, then earliest: a long decent stretch
        // is more useful to plan around than one perfect hour.
        val best = runs.maxWithOrNull(
            compareBy<List<HourScore>> { it.size }
                .thenBy { it.map(HourScore::score).average() }
                .thenByDescending { horizon.indexOf(it.first().hour) },
        )

        val confidence = confidenceFor(type, horizon, aqiByHour)
        if (best == null) {
            // Nothing reaches the bar. Reporting the least-bad hour as a
            // recommendation would be worse than saying there isn't one.
            val worstCase = scored.maxByOrNull { it.score }
            return ActivityWindow(
                type = type,
                bestHourScore = worstCase?.score ?: 0,
                limitingFactors = worstCase?.limitingFactors().orEmpty(),
                confidence = confidence,
            )
        }

        return ActivityWindow(
            type = type,
            start = best.first().hour.time,
            end = best.last().hour.time,
            score = best.map(HourScore::score).average().roundToInt(),
            bestHourScore = best.maxOf(HourScore::score),
            // Across the window, not at its best hour: the factor that holds
            // the whole stretch back is the one worth naming.
            limitingFactors = averagedLimitingFactors(best),
            confidence = confidence,
        )
    }

    /**
     * Per-factor scores for one hour.
     *
     * A factor whose input is missing is left out rather than defaulted. The
     * current-conditions evaluator scores an absent AQI as 80, which is a
     * guess presented as a measurement; here the factor simply is not part of
     * the average, and the confidence drops instead.
     */
    internal fun scoreHour(
        type: ActivityType,
        hour: HourlyConditions,
        thresholds: ActivityThresholds,
        aqi: Int?,
    ): Map<ActivityFactor, Int> {
        val factors = mutableMapOf<ActivityFactor, Int>()
        val wanted = type.windowFactors()

        if (ActivityFactor.TEMPERATURE in wanted) {
            factors[ActivityFactor.TEMPERATURE] = thresholds.temperatureScore(hour.temperature)
        }
        if (ActivityFactor.RAIN in wanted) {
            factors[ActivityFactor.RAIN] = thresholds.rainScore(hour.precipitationProbability)
        }
        if (ActivityFactor.WIND in wanted) {
            hour.windSpeed?.let { factors[ActivityFactor.WIND] = thresholds.windScore(it) }
        }
        if (ActivityFactor.UV in wanted) {
            hour.uvIndex?.let { factors[ActivityFactor.UV] = thresholds.uvScore(it) }
        }
        if (ActivityFactor.HUMIDITY in wanted) {
            hour.humidity?.let { factors[ActivityFactor.HUMIDITY] = humidityWindowScore(it) }
        }
        if (ActivityFactor.CLOUD in wanted) {
            hour.cloudCover?.let { factors[ActivityFactor.CLOUD] = cloudWindowScore(it) }
        }
        if (ActivityFactor.AIR_QUALITY in wanted) {
            aqi?.let { factors[ActivityFactor.AIR_QUALITY] = thresholds.aqiScore(it) }
        }
        return factors
    }

    private fun confidenceFor(
        type: ActivityType,
        horizon: List<HourlyConditions>,
        aqiByHour: Map<LocalDateTime, Int>,
    ): ActivityWindowConfidence {
        if (horizon.isEmpty()) return ActivityWindowConfidence.NONE

        val wanted = type.windowFactors()
        val expected = horizon.size * wanted.size
        val present = horizon.sumOf { hour ->
            wanted.count { factor -> factor.isPresent(hour, aqiByHour[hour.time]) }
        }
        val completeness = present.toDouble() / expected
        val shortHorizon = horizon.size < HORIZON_HOURS / 2

        return when {
            shortHorizon -> ActivityWindowConfidence.LOW
            completeness >= 0.95 -> ActivityWindowConfidence.HIGH
            completeness >= 0.75 -> ActivityWindowConfidence.MEDIUM
            else -> ActivityWindowConfidence.LOW
        }
    }

    private fun averagedLimitingFactors(window: List<HourScore>): List<ActivityFactor> {
        val means = window
            .flatMap { it.factors.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, scores) -> scores.average() }
        // Only what is actually dragging the window down; a window where
        // everything is fine should name nothing.
        return means.filterValues { it < GOOD_SCORE }
            .entries
            .sortedBy { it.value }
            .map { it.key }
    }

    private data class HourScore(
        val hour: HourlyConditions,
        val factors: Map<ActivityFactor, Int>,
    ) {
        /**
         * The worst factor, not the average of them.
         *
         * Averaging made a 95% chance of rain score 75 for running, because
         * three comfortable factors outvoted it, and the whole rained-out
         * afternoon came back as a recommended window. An hour is only as good
         * as the thing most wrong with it, which is also what makes the
         * limiting factor an honest explanation rather than a footnote.
         */
        val score: Int
            get() = factors.values.minOrNull() ?: 0

        fun limitingFactors(): List<ActivityFactor> =
            factors.filterValues { it < GOOD_SCORE }.entries.sortedBy { it.value }.map { it.key }
    }
}

private fun humidityWindowScore(humidity: Int): Int = when {
    humidity in 30..65 -> 100
    humidity in 20..75 -> 70
    humidity in 10..85 -> 40
    else -> 20
}

private fun cloudWindowScore(cover: Int): Int = when {
    cover < 20 -> 100
    cover < 40 -> 75
    cover < 60 -> 45
    cover < 80 -> 20
    else -> 5
}

/** The inputs an activity's score is built from. */
enum class ActivityFactor {
    TEMPERATURE,
    RAIN,
    WIND,
    UV,
    HUMIDITY,
    CLOUD,
    AIR_QUALITY,
    ;

    fun isPresent(hour: HourlyConditions, aqi: Int?): Boolean = when (this) {
        TEMPERATURE, RAIN -> true
        WIND -> hour.windSpeed != null
        UV -> hour.uvIndex != null
        HUMIDITY -> hour.humidity != null
        CLOUD -> hour.cloudCover != null
        AIR_QUALITY -> aqi != null
    }
}

/**
 * Which factors matter for each activity.
 *
 * Kept beside the current-conditions factor map it mirrors; the two disagree
 * only in that this one names an enum rather than a display string, so a
 * factor cannot be added to the window scoring without a label for it.
 */
internal fun ActivityType.windowFactors(): Set<ActivityFactor> = when (this) {
    ActivityType.RUNNING -> setOf(
        ActivityFactor.TEMPERATURE,
        ActivityFactor.WIND,
        ActivityFactor.RAIN,
        ActivityFactor.UV,
        ActivityFactor.AIR_QUALITY,
    )
    ActivityType.CYCLING -> setOf(
        ActivityFactor.TEMPERATURE,
        ActivityFactor.WIND,
        ActivityFactor.RAIN,
        ActivityFactor.UV,
        ActivityFactor.AIR_QUALITY,
    )
    ActivityType.FISHING -> setOf(
        ActivityFactor.TEMPERATURE,
        ActivityFactor.WIND,
        ActivityFactor.RAIN,
        ActivityFactor.HUMIDITY,
    )
    ActivityType.STARGAZING -> setOf(
        ActivityFactor.CLOUD,
        ActivityFactor.HUMIDITY,
        ActivityFactor.WIND,
        ActivityFactor.RAIN,
    )
    ActivityType.GRILLING -> setOf(
        ActivityFactor.TEMPERATURE,
        ActivityFactor.RAIN,
        ActivityFactor.WIND,
        ActivityFactor.HUMIDITY,
    )
    ActivityType.LAWN_CARE -> setOf(
        ActivityFactor.TEMPERATURE,
        ActivityFactor.RAIN,
        ActivityFactor.WIND,
        ActivityFactor.UV,
        ActivityFactor.HUMIDITY,
    )
}

/** How much of the data the window was judged on was actually there. */
enum class ActivityWindowConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

@Stable
data class ActivityWindow(
    val type: ActivityType,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val score: Int = 0,
    val bestHourScore: Int = 0,
    val limitingFactors: List<ActivityFactor> = emptyList(),
    val confidence: ActivityWindowConfidence = ActivityWindowConfidence.NONE,
) {
    /** True when no stretch of the next 24 hours was worth recommending. */
    val hasWindow: Boolean get() = start != null && end != null

    val hourCount: Int
        get() = if (start == null || end == null) {
            0
        } else {
            java.time.Duration.between(start, end).toHours().toInt() + 1
        }
}
