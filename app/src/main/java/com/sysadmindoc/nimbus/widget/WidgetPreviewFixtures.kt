package com.sysadmindoc.nimbus.widget

import android.content.Context
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Representative weather used for the launcher's widget picker previews.
 *
 * The picker renders these before any real data exists, so the numbers are
 * invented — but the labels are not. Hours and weekdays are formatted from the
 * device clock through the same helpers the refresh worker uses, so a preview
 * reads in the viewer's language and time format instead of showing a frozen
 * English snapshot.
 */
internal fun previewWeatherData(context: Context, now: LocalDateTime = LocalDateTime.now()): WidgetWeatherData {
    val nowLabel = context.getString(R.string.common_now)
    val todayLabel = context.getString(R.string.today)
    val tomorrowLabel = context.getString(R.string.widget_tomorrow_short)
    val hourlyTemps = listOf(21, 22, 23, 23, 22, 20, 19, 18, 17, 17, 16, 16)
    val hourlyCodes = listOf(0, 0, 1, 1, 2, 2, 3, 61, 61, 3, 2, 1)
    val dailyHighs = listOf(24, 26, 22, 19, 21, 25, 27)
    val dailyLows = listOf(15, 17, 14, 12, 13, 16, 18)
    val dailyCodes = listOf(1, 0, 61, 63, 2, 0, 0)

    return WidgetWeatherData(
        locationName = context.getString(R.string.widget_preview_location),
        temperature = 21.0,
        feelsLike = 20.0,
        high = 24.0,
        low = 15.0,
        weatherCode = 1,
        isDay = now.hour in 6..19,
        humidity = 58,
        windSpeed = 11.0,
        hourly = hourlyTemps.indices.map { index ->
            val time = now.plusHours(index.toLong())
            WidgetHourly(
                hour = if (index == 0) nowLabel else WeatherFormatter.formatHourLabel(time),
                temp = hourlyTemps[index],
                code = hourlyCodes[index],
                isDay = time.hour in 6..19,
                precipChance = if (hourlyCodes[index] >= 61) 70 else 10,
            )
        },
        daily = dailyHighs.indices.map { index ->
            WidgetDaily(
                day = when (index) {
                    0 -> todayLabel
                    1 -> tomorrowLabel
                    else -> now.plusDays(index.toLong()).dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                },
                high = dailyHighs[index],
                low = dailyLows[index],
                code = dailyCodes[index],
                precipChance = if (dailyCodes[index] >= 61) 65 else 10,
            )
        },
        // The picker shows a freshness badge; anchoring it to "now" keeps the
        // preview from advertising stale data.
        updatedAt = System.currentTimeMillis(),
        observedAt = System.currentTimeMillis(),
    )
}

/** Representative saved-city rows for the saved-cities widget preview. */
internal fun previewSavedCities(context: Context): List<WidgetSavedCity> {
    val updatedAt = System.currentTimeMillis()
    return listOf(
        Triple(context.getString(R.string.widget_preview_location), 21, 1),
        Triple(context.getString(R.string.widget_preview_location_second), 27, 0),
        Triple(context.getString(R.string.widget_preview_location_third), 14, 61),
    ).mapIndexed { index, (name, temp, code) ->
        WidgetSavedCity(
            locationId = index.toLong() + 1L,
            locationName = name,
            temperature = temp,
            high = temp + 3,
            low = temp - 6,
            weatherCode = code,
            isDay = true,
            updatedAt = updatedAt,
            observedAt = updatedAt,
        )
    }
}
