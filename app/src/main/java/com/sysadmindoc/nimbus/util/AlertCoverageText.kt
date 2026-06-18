package com.sysadmindoc.nimbus.util

import android.content.Context
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.WeatherAlert

fun WeatherAlert.coverageText(
    context: Context,
    locationName: String? = null,
): String? {
    return when (coversRequestedLocation) {
        true -> locationName
            ?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.alert_coverage_covers_named, it) }
            ?: context.getString(R.string.alert_coverage_covers_current)
        false -> context.getString(R.string.alert_coverage_nearby_polygon)
        null -> null
    }
}
