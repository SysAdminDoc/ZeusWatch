package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.WeatherCode

/**
 * Maps WMO weather codes to Meteocons Lottie animation filenames.
 * Meteocons by Bas Milius (MIT license): https://github.com/basmilius/weather-icons
 *
 * Lottie JSON files should be placed in assets/meteocons/ directory.
 * Use the "fill" style variants for best visibility on dark backgrounds.
 */
object MeteoconMapper {

    /** A Meteocon asset variant pair — same filename day and night unless overridden. */
    private data class Variant(val day: String, val night: String) {
        constructor(name: String) : this(name, name)
    }

    /**
     * Static lookup table from WMO weather code to Meteocon filename pair.
     * Table-driven keeps cyclomatic complexity low (was a 28-branch `when`)
     * and makes additions a one-line entry instead of a code change.
     */
    private val ASSETS: Map<WeatherCode, Variant> = mapOf(
        WeatherCode.CLEAR_SKY to Variant(day = "clear-day", night = "clear-night"),
        WeatherCode.MAINLY_CLEAR to Variant(day = "partly-cloudy-day", night = "partly-cloudy-night"),
        WeatherCode.PARTLY_CLOUDY to Variant(day = "partly-cloudy-day", night = "partly-cloudy-night"),
        WeatherCode.OVERCAST to Variant("overcast"),
        WeatherCode.FOG to Variant("fog"),
        WeatherCode.DEPOSITING_RIME_FOG to Variant("fog"),
        WeatherCode.DRIZZLE_LIGHT to Variant(day = "partly-cloudy-day-drizzle", night = "partly-cloudy-night-drizzle"),
        WeatherCode.DRIZZLE_MODERATE to Variant("drizzle"),
        WeatherCode.DRIZZLE_DENSE to Variant("drizzle"),
        WeatherCode.FREEZING_DRIZZLE_LIGHT to Variant("sleet"),
        WeatherCode.FREEZING_DRIZZLE_DENSE to Variant("sleet"),
        WeatherCode.RAIN_SLIGHT to Variant(day = "partly-cloudy-day-rain", night = "partly-cloudy-night-rain"),
        WeatherCode.RAIN_MODERATE to Variant("rain"),
        WeatherCode.RAIN_HEAVY to Variant("extreme-rain"),
        WeatherCode.FREEZING_RAIN_LIGHT to Variant("sleet"),
        WeatherCode.FREEZING_RAIN_HEAVY to Variant("extreme-sleet"),
        WeatherCode.SNOW_SLIGHT to Variant(day = "partly-cloudy-day-snow", night = "partly-cloudy-night-snow"),
        WeatherCode.SNOW_MODERATE to Variant("snow"),
        WeatherCode.SNOW_HEAVY to Variant("extreme-snow"),
        WeatherCode.SNOW_GRAINS to Variant("snow"),
        WeatherCode.RAIN_SHOWERS_SLIGHT to Variant(day = "partly-cloudy-day-rain", night = "partly-cloudy-night-rain"),
        WeatherCode.RAIN_SHOWERS_MODERATE to Variant("rain"),
        WeatherCode.RAIN_SHOWERS_VIOLENT to Variant("extreme-rain"),
        WeatherCode.SNOW_SHOWERS_SLIGHT to Variant(day = "partly-cloudy-day-snow", night = "partly-cloudy-night-snow"),
        WeatherCode.SNOW_SHOWERS_HEAVY to Variant("extreme-snow"),
        WeatherCode.THUNDERSTORM to Variant("thunderstorms"),
        WeatherCode.THUNDERSTORM_HAIL_SLIGHT to Variant("thunderstorms-rain"),
        WeatherCode.THUNDERSTORM_HAIL_HEAVY to Variant("thunderstorms-extreme-rain"),
        WeatherCode.UNKNOWN to Variant("not-available"),
    )

    private val DEFAULT_VARIANT = Variant("not-available")

    /**
     * Returns the Lottie asset path for the given weather code and day/night.
     * Falls back to a generic icon if no exact match.
     */
    fun getLottieAsset(weatherCode: WeatherCode, isDay: Boolean): String {
        val variant = ASSETS[weatherCode] ?: DEFAULT_VARIANT
        val filename = if (isDay) variant.day else variant.night
        return "meteocons/$filename.json"
    }
}
