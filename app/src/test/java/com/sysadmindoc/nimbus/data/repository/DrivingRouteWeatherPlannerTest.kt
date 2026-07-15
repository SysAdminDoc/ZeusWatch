package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DrivingRouteWeatherPlannerTest {
    private val planner = DrivingRouteWeatherPlanner()

    @Test
    fun `samples an imported route by cumulative geometry distance`() {
        val departure = LocalDateTime.of(2026, 7, 15, 8, 0)
        val geometry = DrivingRouteGeometry(
            points = listOf(
                DrivingRoutePoint(0.0, 0.0),
                DrivingRoutePoint(0.0, 1.0),
                DrivingRoutePoint(1.0, 1.0),
            ),
            estimateKind = DrivingRouteEstimateKind.GPX_ROUTE,
        )

        val plan = planner.plan(geometry, departure, averageSpeedKmh = 111.2)

        assertEquals(3, plan.samples.size)
        val midpoint = plan.samples[1]
        assertEquals(0.0, midpoint.latitude, 0.01)
        assertEquals(1.0, midpoint.longitude, 0.01)
        assertEquals(plan.distanceKm / 2.0, midpoint.distanceFromStartKm, 0.01)
        assertEquals(departure.plusMinutes(plan.estimatedDurationMinutes / 2), midpoint.arrivalTime)
        assertTrue(plan.distanceKm > 220.0)
    }

    @Test
    fun `straight line geometry remains explicitly classified as corridor estimate`() {
        val geometry = DrivingRouteGeometry.straightLine(39.7392, -104.9903, 40.015, -105.2705)

        assertEquals(DrivingRouteEstimateKind.STRAIGHT_LINE_CORRIDOR, geometry.estimateKind)
    }

    @Test
    fun `sampling across antimeridian follows the short segment`() {
        val geometry = DrivingRouteGeometry(
            points = listOf(
                DrivingRoutePoint(0.0, 179.0),
                DrivingRoutePoint(0.0, -179.0),
            ),
            estimateKind = DrivingRouteEstimateKind.GPX_TRACK,
        )

        val plan = planner.plan(
            geometry = geometry,
            departureTime = LocalDateTime.of(2026, 7, 15, 8, 0),
            averageSpeedKmh = 100.0,
        )

        assertEquals(3, plan.samples.size)
        assertTrue(kotlin.math.abs(plan.samples[1].longitude) > 179.0)
    }
}
