package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.MoonPhase

internal val MoonPhase.labelRes: Int
    get() = when (this) {
        MoonPhase.NEW_MOON -> R.string.moon_phase_new_moon
        MoonPhase.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
        MoonPhase.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        MoonPhase.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
        MoonPhase.FULL_MOON -> R.string.moon_phase_full_moon
        MoonPhase.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
        MoonPhase.LAST_QUARTER -> R.string.moon_phase_last_quarter
        MoonPhase.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
    }
