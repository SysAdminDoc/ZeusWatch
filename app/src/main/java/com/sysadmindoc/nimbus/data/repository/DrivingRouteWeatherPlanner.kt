package com.sysadmindoc.nimbus.data.repository

import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class DrivingRoutePoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid route latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid route longitude" }
    }
}

data class DrivingRouteGeometry(
    val points: List<DrivingRoutePoint>,
    val estimateKind: DrivingRouteEstimateKind,
) {
    init {
        require(points.size >= 2) { "A route requires at least two points" }
    }

    companion object {
        fun straightLine(
            originLatitude: Double,
            originLongitude: Double,
            destinationLatitude: Double,
            destinationLongitude: Double,
        ): DrivingRouteGeometry = DrivingRouteGeometry(
            points = listOf(
                DrivingRoutePoint(originLatitude, originLongitude),
                DrivingRoutePoint(destinationLatitude, destinationLongitude),
            ),
            estimateKind = DrivingRouteEstimateKind.STRAIGHT_LINE_CORRIDOR,
        )
    }
}

data class DrivingRouteSample(
    val index: Int,
    val latitude: Double,
    val longitude: Double,
    val distanceFromStartKm: Double,
    val arrivalTime: LocalDateTime,
)

data class DrivingRouteGeometryPlan(
    val distanceKm: Double,
    val estimatedDurationMinutes: Long,
    val assumedSpeedKmh: Double,
    val samples: List<DrivingRouteSample>,
)

/**
 * Samples weather positions by cumulative polyline distance. This estimates
 * conditions along supplied geometry; it never calculates or claims a
 * navigation route.
 */
class DrivingRouteWeatherPlanner {
    fun plan(
        geometry: DrivingRouteGeometry,
        departureTime: LocalDateTime,
        averageSpeedKmh: Double,
    ): DrivingRouteGeometryPlan {
        val speedKmh = averageSpeedKmh.coerceAtLeast(MINIMUM_ASSUMED_SPEED_KMH)
        val cumulativeDistances = buildList {
            add(0.0)
            geometry.points.zipWithNext().forEach { (start, end) ->
                add(last() + haversineKm(start, end))
            }
        }
        val totalDistanceKm = cumulativeDistances.last()
        val durationMinutes = ((totalDistanceKm / speedKmh) * 60.0)
            .roundToLong()
            .coerceAtLeast(1L)
        val sampleFractions = sampleFractions(totalDistanceKm)
        val samples = sampleFractions.mapIndexed { index, fraction ->
            val targetDistanceKm = totalDistanceKm * fraction
            val point = pointAtDistance(
                points = geometry.points,
                cumulativeDistances = cumulativeDistances,
                targetDistanceKm = targetDistanceKm,
            )
            DrivingRouteSample(
                index = index,
                latitude = point.latitude,
                longitude = point.longitude,
                distanceFromStartKm = targetDistanceKm,
                arrivalTime = departureTime.plusMinutes((durationMinutes * fraction).roundToLong()),
            )
        }
        return DrivingRouteGeometryPlan(
            distanceKm = totalDistanceKm,
            estimatedDurationMinutes = durationMinutes,
            assumedSpeedKmh = speedKmh,
            samples = samples,
        )
    }

    private fun pointAtDistance(
        points: List<DrivingRoutePoint>,
        cumulativeDistances: List<Double>,
        targetDistanceKm: Double,
    ): DrivingRoutePoint {
        if (targetDistanceKm <= 0.0) return points.first()
        if (targetDistanceKm >= cumulativeDistances.last()) return points.last()

        val endIndex = cumulativeDistances.indexOfFirst { it >= targetDistanceKm }
            .coerceAtLeast(1)
        val segmentStartDistance = cumulativeDistances[endIndex - 1]
        val segmentLength = cumulativeDistances[endIndex] - segmentStartDistance
        if (segmentLength <= 0.0) return points[endIndex]
        val fraction = (targetDistanceKm - segmentStartDistance) / segmentLength
        val start = points[endIndex - 1]
        val end = points[endIndex]
        return DrivingRoutePoint(
            latitude = interpolate(start.latitude, end.latitude, fraction),
            longitude = interpolateLongitude(start.longitude, end.longitude, fraction),
        )
    }

    private fun sampleFractions(distanceKm: Double): List<Double> {
        val segmentCount = when {
            distanceKm < 80.0 -> 1
            distanceKm < 240.0 -> 2
            distanceKm < 520.0 -> 3
            distanceKm < 900.0 -> 4
            else -> 5
        }
        return (0..segmentCount).map { index -> index.toDouble() / segmentCount.toDouble() }
    }

    private fun interpolate(start: Double, end: Double, fraction: Double): Double =
        start + ((end - start) * fraction)

    private fun interpolateLongitude(start: Double, end: Double, fraction: Double): Double {
        val shortestDelta = ((end - start + 540.0) % 360.0) - 180.0
        val longitude = start + (shortestDelta * fraction)
        return ((longitude + 540.0) % 360.0) - 180.0
    }

    private fun haversineKm(start: DrivingRoutePoint, end: DrivingRoutePoint): Double {
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_KM * c
    }

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0
        const val MINIMUM_ASSUMED_SPEED_KMH = 20.0
    }
}
