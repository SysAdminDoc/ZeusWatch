package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.model.CustomAlertUnit
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import java.util.Locale

/** A custom-rule evaluation hit, ready to surface as a notification. */
data class TriggeredCustomAlert(
    val rule: CustomAlertRule,
    /** Observed value, in canonical units. */
    val observedCanonical: Double,
)

/**
 * Evaluate [rules] against [data]. Disabled rules are skipped. Rules whose
 * required metric is unavailable in this data (e.g. API omitted the daily
 * UV max) are also skipped — evaluating them as "threshold not met" would
 * silently mask a data problem.
 *
 * Kept as a top-level pure function so it can be unit-tested without
 * WorkManager or Hilt scaffolding.
 */
internal fun evaluateCustomAlertRules(
    rules: List<CustomAlertRule>,
    data: WeatherData,
): List<TriggeredCustomAlert> {
    val today = data.daily.firstOrNull()
    val tonight = data.daily.getOrNull(0)
    val next12h = data.hourly.take(12)
    val next24h = data.hourly.take(24)

    val results = mutableListOf<TriggeredCustomAlert>()
    for (rule in rules) {
        if (!rule.enabled) continue
        val observed = when (rule.metric) {
            CustomAlertMetric.TEMP_HIGH_TODAY -> today?.temperatureHigh
            CustomAlertMetric.TEMP_LOW_TONIGHT -> tonight?.temperatureLow
            CustomAlertMetric.WIND_GUST_NEXT_12H -> next12h.mapNotNull { it.windGusts ?: it.windSpeed }.maxOrNull()
            CustomAlertMetric.PRECIP_SUM_NEXT_24H -> next24h.sumOf { it.precipitation ?: 0.0 }.takeIf { next24h.isNotEmpty() }
            CustomAlertMetric.UV_INDEX_MAX_TODAY -> today?.uvIndexMax ?: next12h.mapNotNull { it.uvIndex }.maxOrNull()
        } ?: continue

        val triggers = when (rule.operator) {
            CustomAlertOperator.GREATER_THAN -> observed > rule.thresholdCanonical
            CustomAlertOperator.LESS_THAN -> observed < rule.thresholdCanonical
        }
        if (triggers) {
            results += TriggeredCustomAlert(rule = rule, observedCanonical = observed)
        }
    }
    return results
}

/**
 * Human-readable phrasing for a triggered rule, localized to the user's
 * display units. Used as the notification body.
 */
internal fun formatTriggeredAlert(
    triggered: TriggeredCustomAlert,
    settings: NimbusSettings,
): Pair<String, String> {
    val rule = triggered.rule
    val observed = convertForDisplay(triggered.observedCanonical, rule.metric, settings)
    val threshold = convertForDisplay(rule.thresholdCanonical, rule.metric, settings)
    val unitLabel = displayUnitLabel(rule.metric, settings)

    val observedFmt = formatWithPrecision(observed, rule.metric)
    val thresholdFmt = formatWithPrecision(threshold, rule.metric)

    val title = "${rule.metric.label} ${rule.operator.symbol} ${thresholdFmt}${unitLabel}"
    val body = "${rule.metric.summary} ${rule.operator.label} your threshold " +
        "(${thresholdFmt}${unitLabel}). Now forecasting ${observedFmt}${unitLabel}."
    return title to body
}

/** Convert a canonical-unit value to the user's display unit. */
internal fun convertForDisplay(
    canonical: Double,
    metric: CustomAlertMetric,
    settings: NimbusSettings,
): Double = when (metric.unit) {
    CustomAlertUnit.CELSIUS -> if (settings.tempUnit == TempUnit.FAHRENHEIT) {
        canonical * 9.0 / 5.0 + 32.0
    } else canonical
    CustomAlertUnit.KMH -> when (settings.windUnit) {
        WindUnit.MPH -> canonical * 0.621371
        WindUnit.MS -> canonical / 3.6
        WindUnit.KMH -> canonical
        WindUnit.KNOTS -> canonical * 0.539957
    }
    CustomAlertUnit.MM -> when (settings.precipUnit) {
        PrecipUnit.INCHES -> canonical / 25.4
        PrecipUnit.MM -> canonical
    }
    CustomAlertUnit.UV -> canonical
}

/** Reverse of [convertForDisplay] — used when saving a user-entered threshold. */
internal fun convertToCanonical(
    displayValue: Double,
    metric: CustomAlertMetric,
    settings: NimbusSettings,
): Double = when (metric.unit) {
    CustomAlertUnit.CELSIUS -> if (settings.tempUnit == TempUnit.FAHRENHEIT) {
        (displayValue - 32.0) * 5.0 / 9.0
    } else displayValue
    CustomAlertUnit.KMH -> when (settings.windUnit) {
        WindUnit.MPH -> displayValue / 0.621371
        WindUnit.MS -> displayValue * 3.6
        WindUnit.KMH -> displayValue
        WindUnit.KNOTS -> displayValue / 0.539957
    }
    CustomAlertUnit.MM -> when (settings.precipUnit) {
        PrecipUnit.INCHES -> displayValue * 25.4
        PrecipUnit.MM -> displayValue
    }
    CustomAlertUnit.UV -> displayValue
}

internal fun displayUnitLabel(metric: CustomAlertMetric, settings: NimbusSettings): String =
    when (metric.unit) {
        CustomAlertUnit.CELSIUS -> if (settings.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
        CustomAlertUnit.KMH -> when (settings.windUnit) {
            WindUnit.MPH -> " mph"
            WindUnit.MS -> " m/s"
            WindUnit.KMH -> " km/h"
            WindUnit.KNOTS -> " kn"
        }
        CustomAlertUnit.MM -> when (settings.precipUnit) {
            PrecipUnit.INCHES -> " in"
            PrecipUnit.MM -> " mm"
        }
        CustomAlertUnit.UV -> ""
    }

private fun formatWithPrecision(value: Double, metric: CustomAlertMetric): String {
    // Temperatures + wind: whole numbers; precip + UV: one decimal.
    return when (metric.unit) {
        CustomAlertUnit.CELSIUS, CustomAlertUnit.KMH ->
            String.format(Locale.US, "%d", kotlin.math.round(value).toInt())
        CustomAlertUnit.MM, CustomAlertUnit.UV ->
            String.format(Locale.US, "%.1f", value)
    }
}
