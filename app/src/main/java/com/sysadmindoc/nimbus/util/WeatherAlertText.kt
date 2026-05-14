package com.sysadmindoc.nimbus.util

import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency

@get:StringRes
internal val AlertSeverity.labelRes: Int
    get() = when (this) {
        AlertSeverity.EXTREME -> R.string.alert_severity_extreme
        AlertSeverity.SEVERE -> R.string.alert_severity_severe
        AlertSeverity.MODERATE -> R.string.alert_severity_moderate
        AlertSeverity.MINOR -> R.string.alert_severity_minor
        AlertSeverity.UNKNOWN -> R.string.alert_severity_unknown
    }

@get:StringRes
internal val AlertUrgency.labelRes: Int
    get() = when (this) {
        AlertUrgency.IMMEDIATE -> R.string.alert_urgency_immediate
        AlertUrgency.EXPECTED -> R.string.alert_urgency_expected
        AlertUrgency.FUTURE -> R.string.alert_urgency_future
        AlertUrgency.PAST -> R.string.alert_urgency_past
        AlertUrgency.UNKNOWN -> R.string.alert_urgency_unknown
    }
