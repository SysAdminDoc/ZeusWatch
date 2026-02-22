package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open-Meteo Forecast API response models.
 * Docs: https://open-meteo.com/en/docs
 */

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    @SerialName("generationtime_ms") val generationTimeMs: Double? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String? = null,
    @SerialName("timezone_abbreviation") val timezoneAbbreviation: String? = null,
    @SerialName("current") val current: CurrentWeather? = null,
    @SerialName("hourly") val hourly: HourlyWeather? = null,
    @SerialName("daily") val daily: DailyWeather? = null,
)

@Serializable
data class CurrentWeather(
    val time: String,
    val interval: Int? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
    @SerialName("precipitation") val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("surface_pressure") val surfacePressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    @SerialName("visibility") val visibility: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
)

@Serializable
data class HourlyWeather(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double?>? = null,
    @SerialName("relative_humidity_2m") val humidity: List<Int?>? = null,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>? = null,
    @SerialName("precipitation") val precipitation: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("cloud_cover") val cloudCover: List<Int?>? = null,
    @SerialName("visibility") val visibility: List<Double?>? = null,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>? = null,
    @SerialName("wind_direction_10m") val windDirection: List<Int?>? = null,
    @SerialName("uv_index") val uvIndex: List<Double?>? = null,
    @SerialName("is_day") val isDay: List<Int?>? = null,
)

@Serializable
data class DailyWeather(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @SerialName("apparent_temperature_max") val apparentTemperatureMax: List<Double?>? = null,
    @SerialName("apparent_temperature_min") val apparentTemperatureMin: List<Double?>? = null,
    @SerialName("sunrise") val sunrise: List<String?>? = null,
    @SerialName("sunset") val sunset: List<String?>? = null,
    @SerialName("uv_index_max") val uvIndexMax: List<Double?>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double?>? = null,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>? = null,
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?>? = null,
    @SerialName("wind_direction_10m_dominant") val windDirectionDominant: List<Int?>? = null,
    @SerialName("precipitation_hours") val precipitationHours: List<Double?>? = null,
)
