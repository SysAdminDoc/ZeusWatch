package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pirate Weather (Dark Sky-compatible) response models.
 * Units: SI (Celsius, m/s, mm, hPa).
 */

@Serializable
data class PirateWeatherResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "",
    val offset: Double = 0.0,
    val currently: PwCurrently? = null,
    val hourly: PwHourlyBlock? = null,
    val daily: PwDailyBlock? = null,
    val alerts: List<PwAlert> = emptyList(),
)

@Serializable
data class PwCurrently(
    val time: Long = 0,
    val summary: String = "",
    val icon: String = "",
    val temperature: Double = 0.0,
    @SerialName("apparentTemperature") val apparentTemperature: Double = 0.0,
    @SerialName("dewPoint") val dewPoint: Double? = null,
    val humidity: Double = 0.0, // 0-1
    val pressure: Double = 0.0, // hPa
    @SerialName("windSpeed") val windSpeed: Double = 0.0,
    @SerialName("windGust") val windGust: Double? = null,
    @SerialName("windBearing") val windBearing: Int = 0,
    @SerialName("cloudCover") val cloudCover: Double = 0.0, // 0-1
    @SerialName("uvIndex") val uvIndex: Double = 0.0,
    val visibility: Double? = null, // km
    @SerialName("precipIntensity") val precipIntensity: Double = 0.0,
    @SerialName("precipProbability") val precipProbability: Double = 0.0,
    @SerialName("precipType") val precipType: String? = null,
)

@Serializable
data class PwHourlyBlock(
    val summary: String = "",
    val icon: String = "",
    val data: List<PwHourly> = emptyList(),
)

@Serializable
data class PwHourly(
    val time: Long = 0,
    val summary: String = "",
    val icon: String = "",
    val temperature: Double = 0.0,
    @SerialName("apparentTemperature") val apparentTemperature: Double? = null,
    @SerialName("dewPoint") val dewPoint: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    @SerialName("windSpeed") val windSpeed: Double? = null,
    @SerialName("windGust") val windGust: Double? = null,
    @SerialName("windBearing") val windBearing: Int? = null,
    @SerialName("cloudCover") val cloudCover: Double? = null,
    @SerialName("uvIndex") val uvIndex: Double? = null,
    val visibility: Double? = null,
    @SerialName("precipIntensity") val precipIntensity: Double? = null,
    @SerialName("precipProbability") val precipProbability: Double? = null,
    @SerialName("precipType") val precipType: String? = null,
)

@Serializable
data class PwDailyBlock(
    val summary: String = "",
    val icon: String = "",
    val data: List<PwDaily> = emptyList(),
)

@Serializable
data class PwDaily(
    val time: Long = 0,
    val summary: String = "",
    val icon: String = "",
    @SerialName("sunriseTime") val sunriseTime: Long? = null,
    @SerialName("sunsetTime") val sunsetTime: Long? = null,
    @SerialName("temperatureHigh") val temperatureHigh: Double = 0.0,
    @SerialName("temperatureLow") val temperatureLow: Double = 0.0,
    @SerialName("apparentTemperatureHigh") val apparentTemperatureHigh: Double? = null,
    @SerialName("apparentTemperatureLow") val apparentTemperatureLow: Double? = null,
    @SerialName("dewPoint") val dewPoint: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    @SerialName("windSpeed") val windSpeed: Double? = null,
    @SerialName("windGust") val windGust: Double? = null,
    @SerialName("windBearing") val windBearing: Int? = null,
    @SerialName("cloudCover") val cloudCover: Double? = null,
    @SerialName("uvIndex") val uvIndex: Double? = null,
    val visibility: Double? = null,
    @SerialName("precipIntensity") val precipIntensity: Double? = null,
    @SerialName("precipIntensityMax") val precipIntensityMax: Double? = null,
    @SerialName("precipProbability") val precipProbability: Double? = null,
    @SerialName("precipType") val precipType: String? = null,
)

@Serializable
data class PwAlert(
    val title: String = "",
    val regions: List<String> = emptyList(),
    val severity: String = "",
    val time: Long = 0,
    val expires: Long = 0,
    val description: String = "",
    val uri: String = "",
)

/**
 * Maps Pirate Weather / Dark Sky icon strings to WMO weather codes.
 */
object PwIconMapper {
    fun toWmoCode(icon: String, precipType: String? = null): Int = when (icon) {
        "clear-day", "clear-night" -> 0
        "partly-cloudy-day", "partly-cloudy-night" -> 2
        "cloudy" -> 3
        "fog" -> 45
        "wind" -> 3 // No WMO wind code; use overcast
        "rain" -> when {
            precipType == "sleet" -> 66
            else -> 63
        }
        "snow" -> 73
        "sleet" -> 66
        "hail" -> 96
        "thunderstorm" -> 95
        "tornado" -> 99
        else -> -1
    }

    fun isDayFromIcon(icon: String): Boolean =
        icon.endsWith("-day") || (!icon.endsWith("-night") && !icon.contains("night"))
}
