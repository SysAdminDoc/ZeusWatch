package com.sysadmindoc.nimbus.util

import android.content.Context
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions

fun CurrentConditions.conditionDescription(context: Context): String =
    sourceConditionText.cleanSourceCondition() ?: weatherCode.localizedDescription(context)

fun HourlyConditions.conditionDescription(context: Context): String =
    sourceConditionText.cleanSourceCondition() ?: weatherCode.localizedDescription(context)

fun DailyConditions.conditionDescription(context: Context): String =
    sourceConditionText.cleanSourceCondition() ?: weatherCode.localizedDescription(context)

private fun String?.cleanSourceCondition(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
