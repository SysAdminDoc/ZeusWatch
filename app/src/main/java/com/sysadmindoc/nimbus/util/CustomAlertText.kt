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
        CustomAlertMetric.DEW_POINT_NOW -> R.string.custom_alert_metric_dew_point_now
        CustomAlertMetric.FEELS_LIKE_NOW -> R.string.custom_alert_metric_feels_like_now
        CustomAlertMetric.SNOWFALL_SUM_NEXT_24H -> R.string.custom_alert_metric_snowfall_sum_next_24h
        CustomAlertMetric.PRESSURE_NOW -> R.string.custom_alert_metric_pressure_now
        CustomAlertMetric.AQI_NOW -> R.string.custom_alert_metric_aqi_now
        CustomAlertMetric.POLLEN_GRASS_PEAK_TODAY -> R.string.custom_alert_metric_pollen_grass
        CustomAlertMetric.POLLEN_BIRCH_PEAK_TODAY -> R.string.custom_alert_metric_pollen_birch
        CustomAlertMetric.POLLEN_RAGWEED_PEAK_TODAY -> R.string.custom_alert_metric_pollen_ragweed
        CustomAlertMetric.POLLEN_OLIVE_PEAK_TODAY -> R.string.custom_alert_metric_pollen_olive
        CustomAlertMetric.POLLEN_ALDER_PEAK_TODAY -> R.string.custom_alert_metric_pollen_alder
        CustomAlertMetric.POLLEN_MUGWORT_PEAK_TODAY -> R.string.custom_alert_metric_pollen_mugwort
    }

@get:StringRes
internal val CustomAlertMetric.summaryRes: Int
    get() = when (this) {
        CustomAlertMetric.TEMP_HIGH_TODAY -> R.string.custom_alert_metric_temp_high_today_summary
        CustomAlertMetric.TEMP_LOW_TONIGHT -> R.string.custom_alert_metric_temp_low_tonight_summary
        CustomAlertMetric.WIND_GUST_NEXT_12H -> R.string.custom_alert_metric_wind_gust_next_12h_summary
        CustomAlertMetric.PRECIP_SUM_NEXT_24H -> R.string.custom_alert_metric_precip_sum_next_24h_summary
        CustomAlertMetric.UV_INDEX_MAX_TODAY -> R.string.custom_alert_metric_uv_index_max_today_summary
        CustomAlertMetric.DEW_POINT_NOW -> R.string.custom_alert_metric_dew_point_now_summary
        CustomAlertMetric.FEELS_LIKE_NOW -> R.string.custom_alert_metric_feels_like_now_summary
        CustomAlertMetric.SNOWFALL_SUM_NEXT_24H -> R.string.custom_alert_metric_snowfall_sum_next_24h_summary
        CustomAlertMetric.PRESSURE_NOW -> R.string.custom_alert_metric_pressure_now_summary
        CustomAlertMetric.AQI_NOW -> R.string.custom_alert_metric_aqi_now_summary
        CustomAlertMetric.POLLEN_GRASS_PEAK_TODAY -> R.string.custom_alert_metric_pollen_grass_summary
        CustomAlertMetric.POLLEN_BIRCH_PEAK_TODAY -> R.string.custom_alert_metric_pollen_birch_summary
        CustomAlertMetric.POLLEN_RAGWEED_PEAK_TODAY -> R.string.custom_alert_metric_pollen_ragweed_summary
        CustomAlertMetric.POLLEN_OLIVE_PEAK_TODAY -> R.string.custom_alert_metric_pollen_olive_summary
        CustomAlertMetric.POLLEN_ALDER_PEAK_TODAY -> R.string.custom_alert_metric_pollen_alder_summary
        CustomAlertMetric.POLLEN_MUGWORT_PEAK_TODAY -> R.string.custom_alert_metric_pollen_mugwort_summary
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
