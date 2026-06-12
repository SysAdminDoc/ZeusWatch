package com.sysadmindoc.nimbus.data.model

import android.content.Context
import androidx.annotation.StringRes
import com.sysadmindoc.nimbus.R

/**
 * WMO Weather interpretation codes (WW) used by Open-Meteo.
 * https://open-meteo.com/en/docs#weathervariables
 */
enum class WeatherCode(
    val code: Int,
    val description: String,
    @StringRes private val descriptionResId: Int,
    val dayIcon: String,
    val nightIcon: String,
) {
    CLEAR_SKY(0, "Clear", R.string.weather_code_clear_sky, "clear_day", "clear_night"),
    MAINLY_CLEAR(1, "Mainly Clear", R.string.weather_code_mainly_clear, "mainly_clear_day", "mainly_clear_night"),
    PARTLY_CLOUDY(2, "Partly Cloudy", R.string.weather_code_partly_cloudy, "partly_cloudy_day", "partly_cloudy_night"),
    OVERCAST(3, "Overcast", R.string.weather_code_overcast, "overcast", "overcast"),
    FOG(45, "Fog", R.string.weather_code_fog, "fog", "fog"),
    DEPOSITING_RIME_FOG(48, "Freezing Fog", R.string.weather_code_depositing_rime_fog, "fog", "fog"),
    DRIZZLE_LIGHT(51, "Light Drizzle", R.string.weather_code_drizzle_light, "drizzle", "drizzle"),
    DRIZZLE_MODERATE(53, "Drizzle", R.string.weather_code_drizzle_moderate, "drizzle", "drizzle"),
    DRIZZLE_DENSE(55, "Heavy Drizzle", R.string.weather_code_drizzle_dense, "drizzle", "drizzle"),
    FREEZING_DRIZZLE_LIGHT(
        56,
        "Light Freezing Drizzle",
        R.string.weather_code_freezing_drizzle_light,
        "freezing_drizzle",
        "freezing_drizzle",
    ),
    FREEZING_DRIZZLE_DENSE(
        57,
        "Freezing Drizzle",
        R.string.weather_code_freezing_drizzle_dense,
        "freezing_drizzle",
        "freezing_drizzle",
    ),
    RAIN_SLIGHT(61, "Light Rain", R.string.weather_code_rain_slight, "rain_light", "rain_light"),
    RAIN_MODERATE(63, "Rain", R.string.weather_code_rain_moderate, "rain", "rain"),
    RAIN_HEAVY(65, "Heavy Rain", R.string.weather_code_rain_heavy, "rain_heavy", "rain_heavy"),
    FREEZING_RAIN_LIGHT(
        66,
        "Light Freezing Rain",
        R.string.weather_code_freezing_rain_light,
        "freezing_rain",
        "freezing_rain",
    ),
    FREEZING_RAIN_HEAVY(
        67,
        "Freezing Rain",
        R.string.weather_code_freezing_rain_heavy,
        "freezing_rain",
        "freezing_rain",
    ),
    SNOW_SLIGHT(71, "Light Snow", R.string.weather_code_snow_slight, "snow_light", "snow_light"),
    SNOW_MODERATE(73, "Snow", R.string.weather_code_snow_moderate, "snow", "snow"),
    SNOW_HEAVY(75, "Heavy Snow", R.string.weather_code_snow_heavy, "snow_heavy", "snow_heavy"),
    SNOW_GRAINS(77, "Snow Grains", R.string.weather_code_snow_grains, "snow", "snow"),
    RAIN_SHOWERS_SLIGHT(80, "Light Showers", R.string.weather_code_rain_showers_slight, "showers_day", "showers_night"),
    RAIN_SHOWERS_MODERATE(81, "Showers", R.string.weather_code_rain_showers_moderate, "showers_day", "showers_night"),
    RAIN_SHOWERS_VIOLENT(82, "Heavy Showers", R.string.weather_code_rain_showers_violent, "showers_heavy", "showers_heavy"),
    SNOW_SHOWERS_SLIGHT(
        85,
        "Light Snow Showers",
        R.string.weather_code_snow_showers_slight,
        "snow_showers_day",
        "snow_showers_night",
    ),
    SNOW_SHOWERS_HEAVY(
        86,
        "Heavy Snow Showers",
        R.string.weather_code_snow_showers_heavy,
        "snow_showers_heavy",
        "snow_showers_heavy",
    ),
    THUNDERSTORM(95, "Thunderstorm", R.string.weather_code_thunderstorm, "thunderstorm", "thunderstorm"),
    THUNDERSTORM_HAIL_SLIGHT(
        96,
        "Thunderstorm with Hail",
        R.string.weather_code_thunderstorm_hail_slight,
        "thunderstorm_hail",
        "thunderstorm_hail",
    ),
    THUNDERSTORM_HAIL_HEAVY(
        99,
        "Severe Thunderstorm",
        R.string.weather_code_thunderstorm_hail_heavy,
        "thunderstorm_hail",
        "thunderstorm_hail",
    ),
    UNKNOWN(-1, "Unknown", R.string.weather_code_unknown, "unknown", "unknown");

    val isRainy: Boolean
        get() = code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)

    val isSnowy: Boolean
        get() = code in listOf(71, 73, 75, 77, 85, 86)

    val isStormy: Boolean
        get() = code in listOf(95, 96, 99)

    val isCloudy: Boolean
        get() = code in listOf(2, 3)

    val isFoggy: Boolean
        get() = code in listOf(45, 48)

    fun iconName(isDay: Boolean): String = if (isDay) dayIcon else nightIcon

    @StringRes
    fun descriptionRes(): Int = descriptionResId

    fun localizedDescription(context: Context): String = context.getString(descriptionRes())

    companion object {
        private val codeMap = entries.associateBy { it.code }

        fun fromCode(code: Int?): WeatherCode = code?.let { codeMap[it] } ?: UNKNOWN
    }
}
