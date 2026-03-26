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

    /**
     * Returns the Lottie asset path for the given weather code and day/night.
     * Falls back to a generic icon if no exact match.
     */
    fun getLottieAsset(weatherCode: WeatherCode, isDay: Boolean): String {
        val filename = when (weatherCode) {
            WeatherCode.CLEAR_SKY -> if (isDay) "clear-day" else "clear-night"
            WeatherCode.MAINLY_CLEAR -> if (isDay) "partly-cloudy-day" else "partly-cloudy-night"
            WeatherCode.PARTLY_CLOUDY -> if (isDay) "partly-cloudy-day" else "partly-cloudy-night"
            WeatherCode.OVERCAST -> "overcast"
            WeatherCode.FOG -> "fog"
            WeatherCode.DEPOSITING_RIME_FOG -> "fog"
            WeatherCode.DRIZZLE_LIGHT -> if (isDay) "partly-cloudy-day-drizzle" else "partly-cloudy-night-drizzle"
            WeatherCode.DRIZZLE_MODERATE -> "drizzle"
            WeatherCode.DRIZZLE_DENSE -> "drizzle"
            WeatherCode.FREEZING_DRIZZLE_LIGHT -> "sleet"
            WeatherCode.FREEZING_DRIZZLE_DENSE -> "sleet"
            WeatherCode.RAIN_SLIGHT -> if (isDay) "partly-cloudy-day-rain" else "partly-cloudy-night-rain"
            WeatherCode.RAIN_MODERATE -> "rain"
            WeatherCode.RAIN_HEAVY -> "extreme-rain"
            WeatherCode.FREEZING_RAIN_LIGHT -> "sleet"
            WeatherCode.FREEZING_RAIN_HEAVY -> "extreme-sleet"
            WeatherCode.SNOW_SLIGHT -> if (isDay) "partly-cloudy-day-snow" else "partly-cloudy-night-snow"
            WeatherCode.SNOW_MODERATE -> "snow"
            WeatherCode.SNOW_HEAVY -> "extreme-snow"
            WeatherCode.SNOW_GRAINS -> "snow"
            WeatherCode.RAIN_SHOWERS_SLIGHT -> if (isDay) "partly-cloudy-day-rain" else "partly-cloudy-night-rain"
            WeatherCode.RAIN_SHOWERS_MODERATE -> "rain"
            WeatherCode.RAIN_SHOWERS_VIOLENT -> "extreme-rain"
            WeatherCode.SNOW_SHOWERS_SLIGHT -> if (isDay) "partly-cloudy-day-snow" else "partly-cloudy-night-snow"
            WeatherCode.SNOW_SHOWERS_HEAVY -> "extreme-snow"
            WeatherCode.THUNDERSTORM -> "thunderstorms"
            WeatherCode.THUNDERSTORM_HAIL_SLIGHT -> "thunderstorms-rain"
            WeatherCode.THUNDERSTORM_HAIL_HEAVY -> "thunderstorms-extreme-rain"
            WeatherCode.UNKNOWN -> "not-available"
        }
        return "meteocons/$filename.json"
    }
}
