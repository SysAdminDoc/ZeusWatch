package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.repository.SkinType

/**
 * Safe unprotected sun exposure, in minutes, before skin reddening begins.
 *
 * The unset baseline is the estimate this app has always shown: roughly
 * `200 / (UVI x 3)` minutes, which corresponds to a minimal erythemal dose of
 * about 100 J/m2. That is more cautious than any published Fitzpatrick value,
 * which is the right default when the app has no idea who is holding it.
 *
 * Choosing a skin type scales that baseline by the ratio of that type's
 * minimal erythemal dose to type I's, using the conventional MED table
 * (I 200, II 250, III 300, IV 450, V 600, VI 1000 J/m2). Type I therefore
 * lands exactly on the existing estimate and every other type gets
 * proportionally longer. No type ever gets a shorter time than the app
 * showed before, so opting in can only move the number the safe way.
 *
 * This is an estimate for clear midday sky with no sunscreen, not medical
 * advice, and it deliberately ignores altitude, surface reflection and
 * cloud cover.
 */
object UvExposureCalculator {

    private const val BASELINE_NUMERATOR = 200.0
    private const val BASELINE_UV_FACTOR = 3.0
    private const val MIN_MINUTES = 5
    private const val MAX_MINUTES = 120

    /** Below UVI 1 there is no meaningful erythemal dose to accumulate. */
    private const val MIN_MEANINGFUL_UV = 1.0

    /**
     * Minutes before reddening, or null when the UV index is too low for the
     * question to mean anything.
     */
    fun safeMinutes(uvIndex: Double, skinType: SkinType = SkinType.NOT_SET): Int? {
        if (uvIndex < MIN_MEANINGFUL_UV) return null
        val baseline = BASELINE_NUMERATOR / (uvIndex * BASELINE_UV_FACTOR)
        return (baseline * skinType.exposureMultiplier).toInt().coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}
