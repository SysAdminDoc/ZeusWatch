package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.PollenLevel

internal val AqiLevel.labelRes: Int
    get() = when (this) {
        AqiLevel.GOOD -> R.string.aqi_level_good
        AqiLevel.MODERATE -> R.string.aqi_level_moderate
        AqiLevel.UNHEALTHY_SENSITIVE -> R.string.aqi_level_unhealthy_sensitive
        AqiLevel.UNHEALTHY -> R.string.aqi_level_unhealthy
        AqiLevel.VERY_UNHEALTHY -> R.string.aqi_level_very_unhealthy
        AqiLevel.HAZARDOUS -> R.string.aqi_level_hazardous
    }

internal val AqiLevel.adviceRes: Int
    get() = when (this) {
        AqiLevel.GOOD -> R.string.aqi_advice_good
        AqiLevel.MODERATE -> R.string.aqi_advice_moderate
        AqiLevel.UNHEALTHY_SENSITIVE -> R.string.aqi_advice_unhealthy_sensitive
        AqiLevel.UNHEALTHY -> R.string.aqi_advice_unhealthy
        AqiLevel.VERY_UNHEALTHY -> R.string.aqi_advice_very_unhealthy
        AqiLevel.HAZARDOUS -> R.string.aqi_advice_hazardous
    }

internal val PollenLevel.labelRes: Int
    get() = when (this) {
        PollenLevel.NONE -> R.string.pollen_level_none
        PollenLevel.LOW -> R.string.pollen_level_low
        PollenLevel.MODERATE -> R.string.pollen_level_moderate
        PollenLevel.HIGH -> R.string.pollen_level_high
        PollenLevel.VERY_HIGH -> R.string.pollen_level_very_high
    }
