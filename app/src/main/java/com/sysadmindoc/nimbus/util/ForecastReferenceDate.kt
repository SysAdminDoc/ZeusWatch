package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.WeatherData
import java.time.LocalDate

/**
 * Best-effort forecast-local date anchor for features that need a "today"
 * concept tied to the viewed location rather than the device timezone.
 */
internal fun weatherReferenceDate(data: WeatherData): LocalDate {
    return data.current.observationTime?.toLocalDate()
        ?: data.daily.firstOrNull()?.date
        ?: data.hourly.firstOrNull()?.time?.toLocalDate()
        ?: data.lastUpdated.toLocalDate()
}
