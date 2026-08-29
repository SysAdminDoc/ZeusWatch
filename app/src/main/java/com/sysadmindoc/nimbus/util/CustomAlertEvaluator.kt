package com.sysadmindoc.nimbus.util

import android.content.Context
import android.util.Log
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.model.CustomAlertUnit
import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.PollenReading
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.PrecipUnit
import com.sysadmindoc.nimbus.data.repository.PressureUnit
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.WindUnit
import java.util.Locale

private const val TAG = "CustomAlertEvaluator"

/**
 * The concentration this reading actually reports, or null when the provider
 * reported nothing for this pollen.
 *
 * Comparing against a `PollenReading.NONE` constant does not work: readings
 * built by the repository carry a `name`, so they never equal the nameless
 * sentinel, and every "below N" rule fired daily where the data is absent.
 * `hasReading` is the flag the repository sets from the raw null.
 */
private fun PollenReading.reportedConcentration(): Double? =
    if (hasReading) concentration else null

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
    airQuality: AirQualityData? = null,
): List<TriggeredCustomAlert> {
    val today = data.daily.firstOrNull()
    val tonight = data.daily.getOrNull(1) ?: data.daily.firstOrNull()
    val next12h = data.hourly.take(12)
    val next24h = data.hourly.take(24)

    val results = mutableListOf<TriggeredCustomAlert>()
    for (rule in rules) {
        if (!rule.enabled) continue
        val observed = observeMetric(rule.metric, today, tonight, next12h, next24h, data, airQuality)
        if (observed == null) {
            // A rule that never fires is indistinguishable from a threshold
            // that is never met unless the skip says so.
            Log.d(TAG, "Skipping ${rule.metric.name}: no value in this data")
            continue
        }

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
    context: Context,
    triggered: TriggeredCustomAlert,
    settings: NimbusSettings,
): Pair<String, String> {
    val rule = triggered.rule
    val observed = convertForDisplay(triggered.observedCanonical, rule.metric, settings)
    val threshold = convertForDisplay(rule.thresholdCanonical, rule.metric, settings)
    val unitLabel = displayUnitLabel(rule.metric, settings)

    val observedFmt = formatWithPrecision(observed, rule.metric)
    val thresholdFmt = formatWithPrecision(threshold, rule.metric)
    val thresholdText = "$thresholdFmt$unitLabel"
    val observedText = "$observedFmt$unitLabel"

    val title = context.getString(
        R.string.custom_alert_notification_title,
        context.customAlertMetricLabel(rule.metric),
        rule.operator.symbol,
        thresholdText,
    )
    val body = context.getString(
        R.string.custom_alert_notification_body,
        context.customAlertMetricSummary(rule.metric),
        context.customAlertOperatorLabel(rule.operator),
        thresholdText,
        observedText,
    )
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
    CustomAlertUnit.HPA -> when (settings.pressureUnit) {
        PressureUnit.INHG -> canonical * 0.02953
        PressureUnit.HPA, PressureUnit.MBAR -> canonical
    }
    // Grains per cubic metre everywhere; no display-unit choice to honour.
    CustomAlertUnit.AQI, CustomAlertUnit.POLLEN -> canonical
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
    CustomAlertUnit.HPA -> when (settings.pressureUnit) {
        PressureUnit.INHG -> displayValue / 0.02953
        PressureUnit.HPA, PressureUnit.MBAR -> displayValue
    }
    CustomAlertUnit.AQI, CustomAlertUnit.POLLEN -> displayValue
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
        CustomAlertUnit.HPA -> when (settings.pressureUnit) {
            PressureUnit.INHG -> " inHg"
            PressureUnit.HPA -> " hPa"
            PressureUnit.MBAR -> " mbar"
        }
        CustomAlertUnit.AQI -> " AQI"
        CustomAlertUnit.POLLEN -> " grains/m³"
    }

private fun formatWithPrecision(value: Double, metric: CustomAlertMetric): String {
    // Temperatures + wind: whole numbers; precip + UV: one decimal.
    return when (metric.unit) {
        CustomAlertUnit.CELSIUS, CustomAlertUnit.KMH ->
            String.format(Locale.US, "%d", kotlin.math.round(value).toInt())
        CustomAlertUnit.MM, CustomAlertUnit.UV ->
            String.format(Locale.US, "%.1f", value)
        CustomAlertUnit.HPA ->
            String.format(Locale.US, "%.1f", value)
        // Counts, not measurements: a fractional grain count is noise.
        CustomAlertUnit.AQI, CustomAlertUnit.POLLEN ->
            kotlin.math.round(value).toInt().toString()
    }
}

/**
 * The observed value for [metric], or null when this data cannot answer it.
 *
 * Split out of [evaluateCustomAlertRules] purely to keep that loop readable
 * as metrics accumulate; null still means "skip", never "threshold not met".
 */
@Suppress("CyclomaticComplexMethod")
private fun observeMetric(
    metric: CustomAlertMetric,
    today: DailyConditions?,
    tonight: DailyConditions?,
    next12h: List<HourlyConditions>,
    next24h: List<HourlyConditions>,
    data: WeatherData,
    airQuality: AirQualityData?,
): Double? = when (metric) {
            CustomAlertMetric.TEMP_HIGH_TODAY -> today?.temperatureHigh
            CustomAlertMetric.TEMP_LOW_TONIGHT -> tonight?.temperatureLow
            CustomAlertMetric.WIND_GUST_NEXT_12H -> next12h.mapNotNull { it.windGusts ?: it.windSpeed }.maxOrNull()
            // Only evaluate when at least one bucket actually reports precip —
            // an all-null series summed as 0.0 would fire false LESS_THAN
            // triggers when the provider omits the field (mirrors the UV
            // branch's missing-data-means-skip behavior documented above).
            CustomAlertMetric.PRECIP_SUM_NEXT_24H ->
                if (next24h.any { it.precipitation != null }) next24h.sumOf { it.precipitation ?: 0.0 } else null
            CustomAlertMetric.UV_INDEX_MAX_TODAY -> today?.uvIndexMax ?: next12h.mapNotNull { it.uvIndex }.maxOrNull()
            CustomAlertMetric.DEW_POINT_NOW -> data.current.dewPoint
            CustomAlertMetric.FEELS_LIKE_NOW -> data.current.feelsLike
            CustomAlertMetric.SNOWFALL_SUM_NEXT_24H ->
                // Open-Meteo snowfall is centimeters; the rule threshold is stored
                // in canonical millimeters (CustomAlertUnit.MM), so convert cm -> mm
                // (x10) before comparing. Without this, a "20 mm" rule only fired at
                // 20 cm (= 200 mm) of snow — a 10x error.
                if (next24h.any { it.snowfall != null }) next24h.sumOf { (it.snowfall ?: 0.0) * 10.0 } else null
            CustomAlertMetric.PRESSURE_NOW -> data.current.pressure.takeIf { it > 0.0 }
            CustomAlertMetric.AQI_NOW -> airQuality?.usAqi?.toDouble()?.takeIf { it > 0.0 }
            // Today's peak, not the current hour: the metric is named
            // PEAK_TODAY and pollen peaks around midday, so a worker running
            // at 07:00 would never fire on a day that peaks at three times
            // the threshold.
            CustomAlertMetric.POLLEN_GRASS_PEAK_TODAY -> airQuality?.pollenPeakToday?.grass?.reportedConcentration()
            CustomAlertMetric.POLLEN_BIRCH_PEAK_TODAY -> airQuality?.pollenPeakToday?.birch?.reportedConcentration()
            CustomAlertMetric.POLLEN_RAGWEED_PEAK_TODAY -> airQuality?.pollenPeakToday?.ragweed?.reportedConcentration()
            CustomAlertMetric.POLLEN_OLIVE_PEAK_TODAY -> airQuality?.pollenPeakToday?.olive?.reportedConcentration()
            CustomAlertMetric.POLLEN_ALDER_PEAK_TODAY -> airQuality?.pollenPeakToday?.alder?.reportedConcentration()
            CustomAlertMetric.POLLEN_MUGWORT_PEAK_TODAY -> airQuality?.pollenPeakToday?.mugwort?.reportedConcentration()
        }
