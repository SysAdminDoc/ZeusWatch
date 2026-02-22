package com.sysadmindoc.nimbus.data.model

import androidx.compose.runtime.Stable
import java.time.LocalDate
import java.time.LocalDateTime

/** UI-ready weather data models, decoupled from API response format. */

@Stable
data class WeatherData(
    val location: LocationInfo,
    val current: CurrentConditions,
    val hourly: List<HourlyConditions>,
    val daily: List<DailyConditions>,
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
)

@Stable
data class LocationInfo(
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
)

@Stable
data class CurrentConditions(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val weatherCode: WeatherCode,
    val isDay: Boolean,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double?,
    val pressure: Double,
    val uvIndex: Double,
    val visibility: Double?,
    val dewPoint: Double?,
    val cloudCover: Int,
    val precipitation: Double,
    val dailyHigh: Double,
    val dailyLow: Double,
    val sunrise: String?,
    val sunset: String?,
)

@Stable
data class HourlyConditions(
    val time: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double?,
    val weatherCode: WeatherCode,
    val isDay: Boolean,
    val precipitationProbability: Int,
    val precipitation: Double?,
    val windSpeed: Double?,
    val windDirection: Int?,
    val humidity: Int?,
    val uvIndex: Double?,
    val cloudCover: Int?,
    val visibility: Double?,
)

@Stable
data class DailyConditions(
    val date: LocalDate,
    val weatherCode: WeatherCode,
    val temperatureHigh: Double,
    val temperatureLow: Double,
    val precipitationProbability: Int,
    val precipitationSum: Double?,
    val sunrise: String?,
    val sunset: String?,
    val uvIndexMax: Double?,
    val windSpeedMax: Double?,
    val windDirectionDominant: Int?,
)
