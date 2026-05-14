package com.sysadmindoc.nimbus.util

import android.content.Context
import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator

@get:StringRes
internal val CustomAlertMetric.labelRes: Int
    get() = when (this) {
        CustomAlertMetric.TEMP_HIGH_TODAY -> R.string.custom_alert_metric_temp_high_today
        CustomAlertMetric.TEMP_LOW_TONIGHT -> R.string.custom_alert_metric_temp_low_tonight
        CustomAlertMetric.WIND_GUST_NEXT_12H -> R.string.custom_alert_metric_wind_gust_next_12h
        CustomAlertMetric.PRECIP_SUM_NEXT_24H -> R.string.custom_alert_metric_precip_sum_next_24h
        CustomAlertMetric.UV_INDEX_MAX_TODAY -> R.string.custom_alert_metric_uv_index_max_today
    }

@get:StringRes
internal val CustomAlertMetric.summaryRes: Int
    get() = when (this) {
        CustomAlertMetric.TEMP_HIGH_TODAY -> R.string.custom_alert_metric_temp_high_today_summary
        CustomAlertMetric.TEMP_LOW_TONIGHT -> R.string.custom_alert_metric_temp_low_tonight_summary
        CustomAlertMetric.WIND_GUST_NEXT_12H -> R.string.custom_alert_metric_wind_gust_next_12h_summary
        CustomAlertMetric.PRECIP_SUM_NEXT_24H -> R.string.custom_alert_metric_precip_sum_next_24h_summary
        CustomAlertMetric.UV_INDEX_MAX_TODAY -> R.string.custom_alert_metric_uv_index_max_today_summary
    }

@get:StringRes
internal val CustomAlertOperator.labelRes: Int
    get() = when (this) {
        CustomAlertOperator.GREATER_THAN -> R.string.custom_alert_operator_above
        CustomAlertOperator.LESS_THAN -> R.string.custom_alert_operator_below
    }

internal fun Context.customAlertMetricLabel(metric: CustomAlertMetric): String =
    getString(metric.labelRes)

internal fun Context.customAlertMetricSummary(metric: CustomAlertMetric): String =
    getString(metric.summaryRes)

internal fun Context.customAlertOperatorLabel(operator: CustomAlertOperator): String =
    getString(operator.labelRes)
