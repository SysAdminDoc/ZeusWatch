package com.sysadmindoc.nimbus.data.repository

import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.util.DrivingAlert
import java.time.LocalDateTime

@Stable
data class DrivingRouteWeatherPlan(
    val origin: LocationInfo,
    val destination: LocationInfo,
    val departureTime: LocalDateTime,
    val estimatedArrivalTime: LocalDateTime,
    val distanceKm: Double,
    val estimatedDurationMinutes: Long,
    val waypoints: List<DrivingRouteWaypoint>,
) {
    val risk: DrivingRouteRiskLevel
        get() = waypoints.maxByOrNull { it.risk.weight }?.risk ?: DrivingRouteRiskLevel.CLEAR
}

@Stable
data class DrivingRouteWaypoint(
    val index: Int,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val arrivalTime: LocalDateTime,
    val distanceFromStartKm: Double,
    val conditions: DrivingRouteConditions,
    val drivingAlerts: List<DrivingAlert>,
    val weatherAlerts: List<WeatherAlert>,
    val risk: DrivingRouteRiskLevel,
)

@Stable
data class DrivingRouteConditions(
    val temperatureC: Double,
    val weatherCode: WeatherCode,
    val precipitationMm: Double,
    val precipitationProbability: Int,
    val windSpeedKmh: Double,
    val windGustKmh: Double?,
    val visibilityMeters: Double?,
    val iceRisk: Boolean,
)

enum class DrivingRouteRiskLevel(val weight: Int) {
    CLEAR(0),
    LOW(1),
    MODERATE(2),
    HIGH(3),
}

class DrivingRoutePlanningException(
    val reason: DrivingRoutePlanningFailure,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause)

enum class DrivingRoutePlanningFailure {
    ORIGIN_REQUIRED,
    DESTINATION_REQUIRED,
    ORIGIN_NOT_FOUND,
    DESTINATION_NOT_FOUND,
    WEATHER_UNAVAILABLE,
}
