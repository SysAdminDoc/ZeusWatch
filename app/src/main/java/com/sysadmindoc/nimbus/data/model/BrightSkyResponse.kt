package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bright Sky (DWD) response models.
 * All temperatures in Celsius, wind in km/h, precipitation in mm, pressure in hPa, visibility in m.
 */

@Serializable
data class BrightSkyWeatherResponse(
    val weather: List<BsWeatherEntry> = emptyList(),
    val sources: List<BsSource> = emptyList(),
)

@Serializable
data class BsWeatherEntry(
    val timestamp: String = "", // ISO 8601
    @SerialName("source_id") val sourceId: Int? = null,
    val temperature: Double? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    val humidity: Double? = null, // percent (0-100) — some stations report as Double
    val pressure: Double? = null, // hPa (actually pressure_msl)
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null, // km/h
    @SerialName("wind_direction") val windDirection: Int? = null,
    @SerialName("wind_gust_speed") val windGustSpeed: Double? = null,
    @SerialName("wind_gust_direction") val windGustDirection: Int? = null,
    val precipitation: Double? = null, // mm in last hour
    @SerialName("precipitation_probability") val precipitationProbability: Double? = null,
    @SerialName("precipitation_probability_6h") val precipitationProbability6h: Double? = null,
    val sunshine: Double? = null, // minutes in last hour
    @SerialName("cloud_cover") val cloudCover: Double? = null, // percent
    val visibility: Double? = null, // meters
    val condition: String? = null, // dry, fog, rain, sleet, snow, hail, thunderstorm
    val icon: String? = null, // clear-day, clear-night, partly-cloudy-day, etc.
    @SerialName("solar_10") val solar10: Double? = null,
    @SerialName("solar_30") val solar30: Double? = null,
    @SerialName("solar_60") val solar60: Double? = null,
)

@Serializable
data class BsSource(
    val id: Int = 0,
    @SerialName("station_name") val stationName: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val height: Double? = null,
    @SerialName("dwd_station_id") val dwdStationId: String? = null,
    @SerialName("observation_type") val observationType: String? = null,
    @SerialName("first_record") val firstRecord: String? = null,
    @SerialName("last_record") val lastRecord: String? = null,
    val distance: Double? = null, // km from requested point
)

// ── Alerts ──────────────────────────────────────────────────────────────

@Serializable
data class BrightSkyAlertResponse(
    val alerts: List<BsAlert> = emptyList(),
)

@Serializable
data class BsAlert(
    val id: Int = 0,
    @SerialName("alert_id") val alertId: String = "",
    @SerialName("effective") val effective: String? = null, // ISO 8601
    @SerialName("onset") val onset: String? = null,
    @SerialName("expires") val expires: String? = null,
    val category: String? = null,
    @SerialName("response_type") val responseType: String? = null,
    val urgency: String? = null,
    val severity: String? = null,
    val certainty: String? = null,
    @SerialName("event_code") val eventCode: Int? = null,
    @SerialName("event_en") val eventEn: String? = null, // English event name
    @SerialName("event_de") val eventDe: String? = null, // German event name
    @SerialName("headline_en") val headlineEn: String? = null,
    @SerialName("headline_de") val headlineDe: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    @SerialName("description_de") val descriptionDe: String? = null,
    @SerialName("instruction_en") val instructionEn: String? = null,
    @SerialName("instruction_de") val instructionDe: String? = null,
)

/**
 * Maps Bright Sky condition/icon strings to WMO weather codes.
 */
object BsConditionMapper {
    fun toWmoCode(condition: String?, icon: String?): Int {
        // Prefer condition string (more specific)
        condition?.let {
            return when (it) {
                "dry" -> if (icon?.contains("cloud") == true) 2 else 0
                "fog" -> 45
                "rain" -> 63
                "sleet" -> 66
                "snow" -> 73
                "hail" -> 96
                "thunderstorm" -> 95
                else -> -1
            }
        }
        // Fall back to icon
        return when (icon) {
            "clear-day", "clear-night" -> 0
            "partly-cloudy-day", "partly-cloudy-night" -> 2
            "cloudy" -> 3
            "fog" -> 45
            "wind" -> 3
            "rain" -> 63
            "sleet" -> 66
            "snow" -> 73
            "hail" -> 96
            "thunderstorm" -> 95
            else -> -1
        }
    }

    fun isDayFromIcon(icon: String?): Boolean =
        icon?.endsWith("-day") == true || (icon?.endsWith("-night") != true)
}
