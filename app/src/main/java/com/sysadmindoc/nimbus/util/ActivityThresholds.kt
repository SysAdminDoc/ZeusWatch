package com.sysadmindoc.nimbus.util

import kotlinx.serialization.Serializable

/**
 * What each person considers comfortable.
 *
 * The current-conditions scoring hardcodes its bands, which is fine for a
 * one-glance number and wrong for planning: someone who runs at 4°C and
 * someone who will not leave the house below 18°C both get told the morning is
 * a 40. These are the thresholds a window is judged against.
 *
 * Stored in settings and round-tripped through export/import, so the defaults
 * here have to stay stable: changing one silently re-scores every window for
 * people who never touched it.
 */
@Serializable
data class ActivityThresholds(
    val minComfortableTempC: Double = DEFAULT_MIN_TEMP_C,
    val maxComfortableTempC: Double = DEFAULT_MAX_TEMP_C,
    val maxPrecipitationChance: Int = DEFAULT_MAX_PRECIP_CHANCE,
    val maxWindKmh: Double = DEFAULT_MAX_WIND_KMH,
    val maxUvIndex: Double = DEFAULT_MAX_UV,
    val maxAqi: Int = DEFAULT_MAX_AQI,
) {

    /**
     * 100 inside the comfortable band, falling off outside it rather than
     * dropping to zero at the edge: one degree over is not the same as ten.
     */
    fun temperatureScore(celsius: Double): Int = when {
        celsius in minComfortableTempC..maxComfortableTempC -> 100
        celsius < minComfortableTempC -> falloff(minComfortableTempC - celsius, perStep = TEMP_FALLOFF_C)
        else -> falloff(celsius - maxComfortableTempC, perStep = TEMP_FALLOFF_C)
    }

    fun rainScore(probability: Int): Int = when {
        probability <= maxPrecipitationChance -> 100
        else -> falloff(
            (probability - maxPrecipitationChance).toDouble(),
            perStep = RAIN_FALLOFF_PERCENT,
        )
    }

    fun windScore(kmh: Double): Int = when {
        kmh <= maxWindKmh -> 100
        else -> falloff(kmh - maxWindKmh, perStep = WIND_FALLOFF_KMH)
    }

    fun uvScore(uv: Double): Int = when {
        uv <= maxUvIndex -> 100
        else -> falloff(uv - maxUvIndex, perStep = UV_FALLOFF)
    }

    fun aqiScore(aqi: Int): Int = when {
        aqi <= maxAqi -> 100
        else -> falloff((aqi - maxAqi).toDouble(), perStep = AQI_FALLOFF)
    }

    /** True when nothing has been changed from the shipped defaults. */
    val isDefault: Boolean get() = this == ActivityThresholds()

    companion object {
        const val DEFAULT_MIN_TEMP_C = 10.0
        const val DEFAULT_MAX_TEMP_C = 28.0
        const val DEFAULT_MAX_PRECIP_CHANCE = 20
        const val DEFAULT_MAX_WIND_KMH = 20.0
        const val DEFAULT_MAX_UV = 6.0
        const val DEFAULT_MAX_AQI = 75

        // The ranges the settings sliders offer. Wide enough to be useful,
        // bounded so an imported file cannot set a threshold nothing can meet.
        val MIN_TEMP_RANGE = -20.0..25.0
        val MAX_TEMP_RANGE = 0.0..45.0
        val PRECIP_RANGE = 0..100
        val WIND_RANGE = 5.0..60.0
        val UV_RANGE = 1.0..11.0
        val AQI_RANGE = 10..200

        // 3 degrees past the band is enough to matter: at 4 a
        // 33-degree afternoon still scored above the good line for
        // someone whose ceiling is 28, so the setting did nothing.
        private const val TEMP_FALLOFF_C = 3.0
        private const val RAIN_FALLOFF_PERCENT = 20.0
        private const val WIND_FALLOFF_KMH = 10.0
        private const val UV_FALLOFF = 2.0
        private const val AQI_FALLOFF = 40.0

        private fun falloff(overshoot: Double, perStep: Double): Int =
            (100 - (overshoot / perStep) * 30).toInt().coerceIn(0, 100)

        /**
         * Clamps an imported or corrupted set back into range.
         *
         * A settings file is user-editable and round-trips through export, so
         * a min above a max, or a threshold outside the slider range, has to
         * come back as something the evaluator can use rather than producing
         * windows nothing ever qualifies for.
         */
        fun sanitize(thresholds: ActivityThresholds): ActivityThresholds {
            val min = thresholds.minComfortableTempC.coerceIn(MIN_TEMP_RANGE)
            val max = thresholds.maxComfortableTempC.coerceIn(MAX_TEMP_RANGE)
            return ActivityThresholds(
                minComfortableTempC = minOf(min, max),
                maxComfortableTempC = maxOf(min, max),
                maxPrecipitationChance = thresholds.maxPrecipitationChance.coerceIn(PRECIP_RANGE),
                maxWindKmh = thresholds.maxWindKmh.coerceIn(WIND_RANGE),
                maxUvIndex = thresholds.maxUvIndex.coerceIn(UV_RANGE),
                maxAqi = thresholds.maxAqi.coerceIn(AQI_RANGE),
            )
        }
    }
}
