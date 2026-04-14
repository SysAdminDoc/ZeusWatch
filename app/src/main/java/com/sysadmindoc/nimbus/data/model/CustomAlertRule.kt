package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * A user-defined weather alert rule.
 *
 * Thresholds are stored in canonical metric units (°C, km/h, mm, UV index)
 * regardless of the user's display-unit preference — so the evaluator math
 * is unit-independent and edit-vs-display conversions happen at the UI layer
 * only. Rule IDs are opaque UUIDs so re-ordering doesn't invalidate the
 * "already triggered this rule today" dedupe set.
 */
@Stable
@Serializable
data class CustomAlertRule(
    val id: String,
    val metric: CustomAlertMetric,
    val operator: CustomAlertOperator,
    /** Threshold in canonical units (see [CustomAlertMetric.unit]). */
    val thresholdCanonical: Double,
    val enabled: Boolean = true,
)

/** What to measure when evaluating the rule. */
enum class CustomAlertMetric(
    val label: String,
    /** One-line human hint used in rule summaries and the settings list row. */
    val summary: String,
    /** The unit the threshold is stored in. Shown in the editor. */
    val unit: CustomAlertUnit,
) {
    TEMP_HIGH_TODAY(
        label = "Today's high",
        summary = "Today's forecast high temperature",
        unit = CustomAlertUnit.CELSIUS,
    ),
    TEMP_LOW_TONIGHT(
        label = "Tonight's low",
        summary = "Tonight's forecast low temperature",
        unit = CustomAlertUnit.CELSIUS,
    ),
    WIND_GUST_NEXT_12H(
        label = "Wind gust (next 12h)",
        summary = "Max wind gust in the next 12 hours",
        unit = CustomAlertUnit.KMH,
    ),
    PRECIP_SUM_NEXT_24H(
        label = "Rain (next 24h)",
        summary = "Total precipitation in the next 24 hours",
        unit = CustomAlertUnit.MM,
    ),
    UV_INDEX_MAX_TODAY(
        label = "Today's UV peak",
        summary = "Today's maximum UV index",
        unit = CustomAlertUnit.UV,
    ),
}

/** Comparison direction. */
enum class CustomAlertOperator(val label: String, val symbol: String) {
    GREATER_THAN("is above", ">"),
    LESS_THAN("is below", "<"),
}

/**
 * Canonical storage unit for a [CustomAlertMetric]. Display labels here are
 * the metric forms — the settings editor converts to/from the user's
 * display-unit preference at the Compose layer.
 */
enum class CustomAlertUnit(val shortLabel: String) {
    CELSIUS("°C"),
    KMH("km/h"),
    MM("mm"),
    UV(""),
}
