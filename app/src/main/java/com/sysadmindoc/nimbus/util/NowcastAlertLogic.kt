package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.MinutelyPrecipitation
import java.time.LocalDateTime

/**
 * Transition detector for proactive precipitation nowcasts.
 *
 * Kept as a pure function in a top-level file so it can be unit-tested
 * without WorkManager / Android context.
 */
sealed class NowcastTransition {
    /** Rain will begin at [startsAt]. [minutesUntil] is the buffer vs. [now]. */
    data class RainStarting(
        val startsAt: LocalDateTime,
        val minutesUntil: Int,
        val peakMm: Double,
    ) : NowcastTransition()

    /** Rain will stop at [endsAt]. [minutesUntil] is the buffer vs. [now]. */
    data class RainStopping(
        val endsAt: LocalDateTime,
        val minutesUntil: Int,
    ) : NowcastTransition()
}

/**
 * Threshold used to classify a 15-minute bucket as "raining".
 * Open-Meteo returns precip in mm per 15-min interval. 0.1 mm/15min is the
 * smallest meaningful wet reading; below that is effectively dry.
 */
internal const val NOWCAST_WET_THRESHOLD_MM = 0.1

/**
 * The farthest we look ahead when classifying a transition. 60 minutes is a
 * reasonable actionable window — beyond that, we're just spamming, and the
 * hourly forecast card covers it better.
 */
internal const val NOWCAST_LOOK_AHEAD_MIN = 60

/**
 * Inspect a minutely-15 precipitation series starting at [now] and report
 * the first meaningful dry→wet or wet→dry transition within
 * [NOWCAST_LOOK_AHEAD_MIN] minutes — or `null` if nothing changes.
 *
 * - "Current" is inferred from the bucket that contains [now]. If no bucket
 *   matches, the earliest future bucket is treated as the current state.
 * - Only the first transition is returned; additional downstream transitions
 *   are ignored so we don't chain "start" and "stop" into a single ping.
 */
internal fun detectNowcastTransition(
    buckets: List<MinutelyPrecipitation>,
    now: LocalDateTime,
    wetThresholdMm: Double = NOWCAST_WET_THRESHOLD_MM,
    lookAheadMinutes: Int = NOWCAST_LOOK_AHEAD_MIN,
): NowcastTransition? {
    if (buckets.size < 2) return null

    // Sort defensively; callers might pass unordered data.
    val sorted = buckets.sortedBy { it.time }
    val cutoff = now.plusMinutes(lookAheadMinutes.toLong())

    // Find the "current" bucket (latest bucket at or before now), otherwise
    // use the first future bucket as current.
    val currentIndex = sorted.indexOfLast { !it.time.isAfter(now) }
        .takeIf { it >= 0 }
        ?: 0

    val currentWet = sorted[currentIndex].precipitation >= wetThresholdMm

    // Look for the first opposite-state bucket within the look-ahead window.
    for (i in (currentIndex + 1) until sorted.size) {
        val bucket = sorted[i]
        if (bucket.time.isAfter(cutoff)) break
        val wet = bucket.precipitation >= wetThresholdMm
        if (wet == currentWet) continue

        val minutesUntil = java.time.Duration.between(now, bucket.time)
            .toMinutes().toInt().coerceAtLeast(0)

        return if (wet) {
            // dry → wet
            val peak = sorted.drop(i)
                .takeWhile { !it.time.isAfter(cutoff) && it.precipitation >= wetThresholdMm }
                .maxOfOrNull { it.precipitation } ?: bucket.precipitation
            NowcastTransition.RainStarting(
                startsAt = bucket.time,
                minutesUntil = minutesUntil,
                peakMm = peak,
            )
        } else {
            // wet → dry
            NowcastTransition.RainStopping(
                endsAt = bucket.time,
                minutesUntil = minutesUntil,
            )
        }
    }
    return null
}
