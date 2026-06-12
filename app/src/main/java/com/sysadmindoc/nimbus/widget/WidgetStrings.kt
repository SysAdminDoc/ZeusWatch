package com.sysadmindoc.nimbus.widget

import android.content.Context
import com.sysadmindoc.nimbus.R
import java.util.Locale

internal data class WidgetStrings(
    val emptyTitle: String,
    val smallEmptyMessage: String,
    val mediumEmptyMessage: String,
    val largeEmptyMessage: String,
    val stripEmptyMessage: String,
    val savedCitiesEmptyMessage: String,
    val currentEyebrow: String,
    val overviewEyebrow: String,
    val detailedOutlookEyebrow: String,
    val savedCitiesEyebrow: String,
    val nextEyebrow: String,
    val next3Days: String,
    val next6Hours: String,
    val fiveDayOutlook: String,
    val tapToOpen: String,
    val feelsHumidityFormat: String,
    val highTempFormat: String,
    val lowTempFormat: String,
    val updatedLive: String,
    val updatedMinutesFormat: String,
    val updatedHoursFormat: String,
    val updatedLiveContentDescription: String,
    val updatedStaleContentDescriptionFormat: String,
    val weatherIconContentDescription: String,
    val savedCityUnavailableTemp: String,
    val savedCityContentDescriptionFormat: String,
) {
    fun feelsHumidity(feelsLike: Int, humidity: Int): String =
        String.format(Locale.getDefault(), feelsHumidityFormat, feelsLike, humidity)

    fun highTemp(value: Int): String = String.format(Locale.getDefault(), highTempFormat, value)

    fun lowTemp(value: Int): String = String.format(Locale.getDefault(), lowTempFormat, value)

    fun updatedLabel(updatedAt: Long): String? =
        widgetUpdatedLabel(
            updatedAt = updatedAt,
            liveLabel = updatedLive,
            minuteFormat = updatedMinutesFormat,
            hourFormat = updatedHoursFormat,
        )

    fun updatedContentDescription(label: String): String =
        if (label == updatedLive) {
            updatedLiveContentDescription
        } else {
            String.format(Locale.getDefault(), updatedStaleContentDescriptionFormat, label)
        }

    fun weatherDescription(code: Int, isDay: Boolean): String =
        WidgetUtils.weatherDescription(code, isDay, weatherIconContentDescription)

    fun savedCityContentDescription(cityName: String, tempLabel: String): String =
        String.format(Locale.getDefault(), savedCityContentDescriptionFormat, cityName, tempLabel)
}

internal fun widgetStrings(context: Context): WidgetStrings = with(context) {
    WidgetStrings(
        emptyTitle = getString(R.string.widget_empty_title),
        smallEmptyMessage = getString(R.string.widget_small_empty_message),
        mediumEmptyMessage = getString(R.string.widget_medium_empty_message),
        largeEmptyMessage = getString(R.string.widget_large_empty_message),
        stripEmptyMessage = getString(R.string.widget_strip_empty_message),
        savedCitiesEmptyMessage = getString(R.string.widget_saved_cities_empty_message),
        currentEyebrow = getString(R.string.widget_current_eyebrow),
        overviewEyebrow = getString(R.string.widget_overview_eyebrow),
        detailedOutlookEyebrow = getString(R.string.widget_detailed_outlook_eyebrow),
        savedCitiesEyebrow = getString(R.string.widget_saved_cities_eyebrow),
        nextEyebrow = getString(R.string.widget_next_eyebrow),
        next3Days = getString(R.string.widget_next_3_days),
        next6Hours = getString(R.string.widget_next_6_hours),
        fiveDayOutlook = getString(R.string.widget_5_day_outlook),
        tapToOpen = getString(R.string.widget_tap_to_open),
        feelsHumidityFormat = getString(R.string.widget_feels_humidity),
        highTempFormat = getString(R.string.widget_high_temp),
        lowTempFormat = getString(R.string.widget_low_temp),
        updatedLive = getString(R.string.widget_updated_live),
        updatedMinutesFormat = getString(R.string.widget_updated_minutes),
        updatedHoursFormat = getString(R.string.widget_updated_hours),
        updatedLiveContentDescription = getString(R.string.widget_updated_live_cd),
        updatedStaleContentDescriptionFormat = getString(R.string.widget_updated_stale_cd),
        weatherIconContentDescription = getString(R.string.widget_weather_icon_cd),
        savedCityUnavailableTemp = getString(R.string.widget_saved_city_unavailable_temp),
        savedCityContentDescriptionFormat = getString(R.string.widget_saved_city_cd),
    )
}
