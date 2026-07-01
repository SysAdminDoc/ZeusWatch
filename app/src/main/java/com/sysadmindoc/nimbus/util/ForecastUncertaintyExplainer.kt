package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandData
import com.sysadmindoc.nimbus.data.repository.ConfidenceBandEntry

object ForecastUncertaintyExplainer {
    fun summarizeHourly(
        hourly: List<HourlyConditions>,
        confidenceBands: ConfidenceBandData?,
        maxHours: Int = 24,
    ): ForecastUncertaintySummary? {
        confidenceBands ?: return null
        val spreads = hourly.take(maxHours).mapNotNull { hour ->
            confidenceBands.entryAt(hour.time)?.spreadC()
        }
        if (spreads.size < 2) return null

        val averageSpreadC = spreads.average()
        return ForecastUncertaintySummary(
            averageSpreadC = averageSpreadC,
            level = classifySpread(averageSpreadC),
            sampleCount = spreads.size,
        )
    }

    fun detailForHour(
        hour: HourlyConditions,
        confidenceBands: ConfidenceBandData?,
    ): ForecastUncertaintyDetail? {
        confidenceBands ?: return null
        return confidenceBands.entryAt(hour.time)?.toDetail()
    }

    fun detailForDay(
        day: DailyConditions,
        confidenceBands: ConfidenceBandData?,
    ): ForecastUncertaintyDetail? {
        confidenceBands ?: return null
        val entries = confidenceBands.entries
            .filter { it.time.toLocalDate() == day.date }
            .mapNotNull { it.validRange() }
        if (entries.isEmpty()) return null

        val averageLowerC = entries.map { it.lowerC }.average()
        val averageUpperC = entries.map { it.upperC }.average()
        val averageSpreadC = entries.map { it.spreadC }.average()
        return ForecastUncertaintyDetail(
            lowerC = averageLowerC,
            upperC = averageUpperC,
            spreadC = averageSpreadC,
            level = classifySpread(averageSpreadC),
            sampleCount = entries.size,
        )
    }

    fun classifySpread(spreadC: Double): ForecastUncertaintyLevel = when {
        spreadC < 2.0 -> ForecastUncertaintyLevel.LOW
        spreadC < 5.0 -> ForecastUncertaintyLevel.MEDIUM
        else -> ForecastUncertaintyLevel.HIGH
    }
}

data class ForecastUncertaintySummary(
    val averageSpreadC: Double,
    val level: ForecastUncertaintyLevel,
    val sampleCount: Int,
)

data class ForecastUncertaintyDetail(
    val lowerC: Double,
    val upperC: Double,
    val spreadC: Double,
    val level: ForecastUncertaintyLevel,
    val sampleCount: Int,
)

enum class ForecastUncertaintyLevel {
    LOW,
    MEDIUM,
    HIGH,
}

private data class ValidConfidenceRange(
    val lowerC: Double,
    val upperC: Double,
    val spreadC: Double,
)

private fun ConfidenceBandEntry.toDetail(): ForecastUncertaintyDetail? {
    val range = validRange() ?: return null
    return ForecastUncertaintyDetail(
        lowerC = range.lowerC,
        upperC = range.upperC,
        spreadC = range.spreadC,
        level = ForecastUncertaintyExplainer.classifySpread(range.spreadC),
        sampleCount = 1,
    )
}

private fun ConfidenceBandEntry.validRange(): ValidConfidenceRange? {
    val spread = spreadC() ?: return null
    return ValidConfidenceRange(
        lowerC = temperatureLower,
        upperC = temperatureUpper,
        spreadC = spread,
    )
}

private fun ConfidenceBandEntry.spreadC(): Double? {
    val spread = temperatureUpper - temperatureLower
    return spread.takeIf { it >= 0.0 && !it.isNaN() && !it.isInfinite() }
}
