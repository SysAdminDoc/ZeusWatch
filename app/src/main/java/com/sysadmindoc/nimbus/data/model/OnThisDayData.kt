package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable

/**
 * Historical weather snapshot for "same calendar date, prior years" context.
 * All temperatures are stored in Celsius (canonical metric); convert at display time.
 */
@Stable
data class OnThisDayData(
    /** Years (newest first) with observed high/low/precip for this calendar date. */
    val priorYears: List<PriorYearEntry>,
    /** Mean of `priorYears` highs, in °C. */
    val averageHighC: Double,
    /** Mean of `priorYears` lows, in °C. */
    val averageLowC: Double,
    /** Max observed high across `priorYears`, in °C. */
    val recordHighC: Double,
    /** Min observed low across `priorYears`, in °C. */
    val recordLowC: Double,
)

@Stable
data class PriorYearEntry(
    val year: Int,
    val highC: Double,
    val lowC: Double,
    val precipMm: Double?,
)
