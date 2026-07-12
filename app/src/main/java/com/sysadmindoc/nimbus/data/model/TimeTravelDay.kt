package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable
import java.time.LocalDate

/**
 * A single day's weather for the "time travel" date scrubber. Sourced either
 * from the Open-Meteo archive (historical observation) or from the loaded
 * 16-day forecast, distinguished by [isHistorical]. All temperatures are
 * canonical metric (°C); precipitation is mm — conversion happens in the UI via
 * `WeatherFormatter`, consistent with the rest of the app.
 */
@Stable
data class TimeTravelDay(
    val date: LocalDate,
    val weatherCode: WeatherCode,
    val highC: Double,
    val lowC: Double,
    val precipMm: Double?,
    val isHistorical: Boolean,
)
