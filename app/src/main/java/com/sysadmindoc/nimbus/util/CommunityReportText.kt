package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.ReportCondition

@get:StringRes
internal val ReportCondition.labelRes: Int
    get() = when (this) {
        ReportCondition.SUNNY -> R.string.report_condition_sunny
        ReportCondition.PARTLY_CLOUDY -> R.string.report_condition_partly_cloudy
        ReportCondition.CLOUDY -> R.string.report_condition_cloudy
        ReportCondition.RAIN -> R.string.report_condition_rain
        ReportCondition.HEAVY_RAIN -> R.string.report_condition_heavy_rain
        ReportCondition.SNOW -> R.string.report_condition_snow
        ReportCondition.FOG -> R.string.report_condition_fog
        ReportCondition.WIND -> R.string.report_condition_wind
        ReportCondition.HAIL -> R.string.report_condition_hail
        ReportCondition.TORNADO -> R.string.report_condition_tornado
    }
