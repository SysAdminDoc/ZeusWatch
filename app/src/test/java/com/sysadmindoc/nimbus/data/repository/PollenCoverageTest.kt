package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollenCoverageTest {

    @Test
    fun `european cities have pollen`() {
        assertTrue(PollenCoverage.isCovered(52.52, 13.40)) // Berlin
        assertTrue(PollenCoverage.isCovered(38.72, -9.14)) // Lisbon
        assertTrue(PollenCoverage.isCovered(64.15, -21.94)) // Reykjavík
        assertTrue(PollenCoverage.isCovered(41.01, 28.98)) // Istanbul
        assertTrue(PollenCoverage.isCovered(71.9, 44.9)) // the domain's north-east corner
    }

    @Test
    fun `places outside the CAMS European domain have none`() {
        assertFalse(PollenCoverage.isCovered(40.71, -74.0)) // New York
        assertFalse(PollenCoverage.isCovered(28.61, 77.21)) // Delhi
        assertFalse(PollenCoverage.isCovered(-33.87, 151.21)) // Sydney
        assertFalse(PollenCoverage.isCovered(55.75, 49.1)) // Kazan, east of 45° E
        assertFalse(PollenCoverage.isCovered(28.1, -15.4)) // Las Palmas, south of 30° N
        assertFalse(PollenCoverage.isCovered(78.22, 15.65)) // Longyearbyen, north of 72° N
    }

    @Test
    fun `the edges match what Open-Meteo returns`() {
        // Each pair was asked of the air quality API on 2026-11-15.
        assertTrue(PollenCoverage.isCovered(30.0, 32.55))
        assertFalse(PollenCoverage.isCovered(29.97, 32.55))
        assertTrue(PollenCoverage.isCovered(71.99, 30.0))
        assertFalse(PollenCoverage.isCovered(72.0, 30.0))
        assertTrue(PollenCoverage.isCovered(50.0, 45.0))
        assertFalse(PollenCoverage.isCovered(50.0, 45.04))
        assertTrue(PollenCoverage.isCovered(50.0, -25.0))
        assertFalse(PollenCoverage.isCovered(50.0, -25.04))
    }
}
