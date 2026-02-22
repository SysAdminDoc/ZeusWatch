package com.sysadmindoc.nimbus.data.model

/**
 * WMO Weather interpretation codes (WW) used by Open-Meteo.
 * https://open-meteo.com/en/docs#weathervariables
 */
enum class WeatherCode(
    val code: Int,
    val description: String,
    val dayIcon: String,
    val nightIcon: String,
) {
    CLEAR_SKY(0, "Clear", "clear_day", "clear_night"),
    MAINLY_CLEAR(1, "Mainly Clear", "mainly_clear_day", "mainly_clear_night"),
    PARTLY_CLOUDY(2, "Partly Cloudy", "partly_cloudy_day", "partly_cloudy_night"),
    OVERCAST(3, "Overcast", "overcast", "overcast"),
    FOG(45, "Fog", "fog", "fog"),
    DEPOSITING_RIME_FOG(48, "Freezing Fog", "fog", "fog"),
    DRIZZLE_LIGHT(51, "Light Drizzle", "drizzle", "drizzle"),
    DRIZZLE_MODERATE(53, "Drizzle", "drizzle", "drizzle"),
    DRIZZLE_DENSE(55, "Heavy Drizzle", "drizzle", "drizzle"),
    FREEZING_DRIZZLE_LIGHT(56, "Light Freezing Drizzle", "freezing_drizzle", "freezing_drizzle"),
    FREEZING_DRIZZLE_DENSE(57, "Freezing Drizzle", "freezing_drizzle", "freezing_drizzle"),
    RAIN_SLIGHT(61, "Light Rain", "rain_light", "rain_light"),
    RAIN_MODERATE(63, "Rain", "rain", "rain"),
    RAIN_HEAVY(65, "Heavy Rain", "rain_heavy", "rain_heavy"),
    FREEZING_RAIN_LIGHT(66, "Light Freezing Rain", "freezing_rain", "freezing_rain"),
    FREEZING_RAIN_HEAVY(67, "Freezing Rain", "freezing_rain", "freezing_rain"),
    SNOW_SLIGHT(71, "Light Snow", "snow_light", "snow_light"),
    SNOW_MODERATE(73, "Snow", "snow", "snow"),
    SNOW_HEAVY(75, "Heavy Snow", "snow_heavy", "snow_heavy"),
    SNOW_GRAINS(77, "Snow Grains", "snow", "snow"),
    RAIN_SHOWERS_SLIGHT(80, "Light Showers", "showers_day", "showers_night"),
    RAIN_SHOWERS_MODERATE(81, "Showers", "showers_day", "showers_night"),
    RAIN_SHOWERS_VIOLENT(82, "Heavy Showers", "showers_heavy", "showers_heavy"),
    SNOW_SHOWERS_SLIGHT(85, "Light Snow Showers", "snow_showers_day", "snow_showers_night"),
    SNOW_SHOWERS_HEAVY(86, "Heavy Snow Showers", "snow_showers_heavy", "snow_showers_heavy"),
    THUNDERSTORM(95, "Thunderstorm", "thunderstorm", "thunderstorm"),
    THUNDERSTORM_HAIL_SLIGHT(96, "Thunderstorm with Hail", "thunderstorm_hail", "thunderstorm_hail"),
    THUNDERSTORM_HAIL_HEAVY(99, "Severe Thunderstorm", "thunderstorm_hail", "thunderstorm_hail"),
    UNKNOWN(-1, "Unknown", "unknown", "unknown");

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

    companion object {
        private val codeMap = entries.associateBy { it.code }

        fun fromCode(code: Int?): WeatherCode = code?.let { codeMap[it] } ?: UNKNOWN
    }
}
