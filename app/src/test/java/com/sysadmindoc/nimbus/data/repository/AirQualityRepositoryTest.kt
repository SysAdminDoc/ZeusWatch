package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.MoonPhase
import com.sysadmindoc.nimbus.data.model.PollenLevel
import com.sysadmindoc.nimbus.data.model.PollenReading
import com.sysadmindoc.nimbus.data.model.PollenThresholdsDb
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AirQualityRepositoryTest {

    private lateinit var repository: AirQualityRepository

    @Before
    fun setup() {
        // API mock not needed for pure function tests
        repository = AirQualityRepository(api = mockk())
    }

    // --- AQI Level mapping ---

    @Test
    fun `AqiLevel fromAqi returns Good for 0-50`() {
        assertEquals(AqiLevel.GOOD, AqiLevel.fromAqi(0))
        assertEquals(AqiLevel.GOOD, AqiLevel.fromAqi(25))
        assertEquals(AqiLevel.GOOD, AqiLevel.fromAqi(50))
    }

    @Test
    fun `AqiLevel fromAqi returns Moderate for 51-100`() {
        assertEquals(AqiLevel.MODERATE, AqiLevel.fromAqi(51))
        assertEquals(AqiLevel.MODERATE, AqiLevel.fromAqi(75))
        assertEquals(AqiLevel.MODERATE, AqiLevel.fromAqi(100))
    }

    @Test
    fun `AqiLevel fromAqi returns Unhealthy Sensitive for 101-150`() {
        assertEquals(AqiLevel.UNHEALTHY_SENSITIVE, AqiLevel.fromAqi(101))
        assertEquals(AqiLevel.UNHEALTHY_SENSITIVE, AqiLevel.fromAqi(150))
    }

    @Test
    fun `AqiLevel fromAqi returns Unhealthy for 151-200`() {
        assertEquals(AqiLevel.UNHEALTHY, AqiLevel.fromAqi(175))
    }

    @Test
    fun `AqiLevel fromAqi returns Very Unhealthy for 201-300`() {
        assertEquals(AqiLevel.VERY_UNHEALTHY, AqiLevel.fromAqi(250))
    }

    @Test
    fun `AqiLevel fromAqi returns Hazardous for 301+`() {
        assertEquals(AqiLevel.HAZARDOUS, AqiLevel.fromAqi(350))
        assertEquals(AqiLevel.HAZARDOUS, AqiLevel.fromAqi(500))
    }

    // --- Astronomy / Moon ---

    @Test
    fun `getAstronomy returns valid moon phase`() {
        val astro = repository.getAstronomy(
            sunrise = "2025-01-15T07:00:00",
            sunset = "2025-01-15T17:30:00",
        )
        assertNotNull(astro.moonPhase)
        assertTrue(astro.moonIllumination in 0.0..100.0)
    }

    @Test
    fun `getAstronomy calculates day length from sunrise and sunset`() {
        val astro = repository.getAstronomy(
            sunrise = "2025-01-15T07:00:00",
            sunset = "2025-01-15T17:30:00",
        )
        assertEquals("10h 30m", astro.dayLength)
    }

    @Test
    fun `getAstronomy handles null sunrise and sunset`() {
        val astro = repository.getAstronomy(sunrise = null, sunset = null)
        assertNull(astro.dayLength)
        assertNotNull(astro.moonPhase)
    }

    @Test
    fun `MoonPhase fromDayOfCycle returns correct phases`() {
        assertEquals(MoonPhase.NEW_MOON, MoonPhase.fromDayOfCycle(0.5))
        assertEquals(MoonPhase.WAXING_CRESCENT, MoonPhase.fromDayOfCycle(4.0))
        assertEquals(MoonPhase.FIRST_QUARTER, MoonPhase.fromDayOfCycle(7.5))
        assertEquals(MoonPhase.WAXING_GIBBOUS, MoonPhase.fromDayOfCycle(11.0))
        assertEquals(MoonPhase.FULL_MOON, MoonPhase.fromDayOfCycle(15.0))
        assertEquals(MoonPhase.WANING_GIBBOUS, MoonPhase.fromDayOfCycle(19.0))
        assertEquals(MoonPhase.LAST_QUARTER, MoonPhase.fromDayOfCycle(23.0))
        assertEquals(MoonPhase.WANING_CRESCENT, MoonPhase.fromDayOfCycle(26.0))
    }

    @Test
    fun `MoonPhase fromDayOfCycle wraps on synodic month`() {
        // 29.53 days is one lunar cycle; 30.0 should wrap to ~0.47 -> NEW_MOON
        assertEquals(MoonPhase.NEW_MOON, MoonPhase.fromDayOfCycle(30.0))
    }

    // --- Pollen ---

    @Test
    fun `PollenReading fromConcentration returns NONE for null or zero`() {
        assertEquals(PollenLevel.NONE, PollenReading.fromConcentration(null, "Test", PollenThresholdsDb.GRASS).level)
        assertEquals(PollenLevel.NONE, PollenReading.fromConcentration(0.0, "Test", PollenThresholdsDb.GRASS).level)
        assertEquals(PollenLevel.NONE, PollenReading.fromConcentration(-1.0, "Test", PollenThresholdsDb.GRASS).level)
    }

    @Test
    fun `PollenReading fromConcentration maps grass thresholds correctly`() {
        // Grass: low=5, moderate=20, high=50
        assertEquals(PollenLevel.LOW, PollenReading.fromConcentration(3.0, "Grass", PollenThresholdsDb.GRASS).level)
        assertEquals(PollenLevel.MODERATE, PollenReading.fromConcentration(10.0, "Grass", PollenThresholdsDb.GRASS).level)
        assertEquals(PollenLevel.HIGH, PollenReading.fromConcentration(30.0, "Grass", PollenThresholdsDb.GRASS).level)
        assertEquals(PollenLevel.VERY_HIGH, PollenReading.fromConcentration(100.0, "Grass", PollenThresholdsDb.GRASS).level)
    }

    @Test
    fun `PollenReading fromConcentration maps birch thresholds correctly`() {
        // Birch: low=10, moderate=50, high=200
        assertEquals(PollenLevel.LOW, PollenReading.fromConcentration(5.0, "Birch", PollenThresholdsDb.BIRCH).level)
        assertEquals(PollenLevel.MODERATE, PollenReading.fromConcentration(30.0, "Birch", PollenThresholdsDb.BIRCH).level)
        assertEquals(PollenLevel.HIGH, PollenReading.fromConcentration(100.0, "Birch", PollenThresholdsDb.BIRCH).level)
        assertEquals(PollenLevel.VERY_HIGH, PollenReading.fromConcentration(300.0, "Birch", PollenThresholdsDb.BIRCH).level)
    }

    // --- AlertSeverity ---

    @Test
    fun `AlertSeverity from parses string correctly`() {
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.EXTREME,
            com.sysadmindoc.nimbus.data.model.AlertSeverity.from("Extreme"))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.SEVERE,
            com.sysadmindoc.nimbus.data.model.AlertSeverity.from("severe"))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.MODERATE,
            com.sysadmindoc.nimbus.data.model.AlertSeverity.from("Moderate"))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.MINOR,
            com.sysadmindoc.nimbus.data.model.AlertSeverity.from("minor"))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.UNKNOWN,
            com.sysadmindoc.nimbus.data.model.AlertSeverity.from(null))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.UNKNOWN,
            com.sysadmindoc.nimbus.data.model.AlertSeverity.from("garbage"))
    }

    // --- AlertUrgency ---

    @Test
    fun `AlertUrgency from parses string correctly`() {
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertUrgency.IMMEDIATE,
            com.sysadmindoc.nimbus.data.model.AlertUrgency.from("Immediate"))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertUrgency.EXPECTED,
            com.sysadmindoc.nimbus.data.model.AlertUrgency.from("expected"))
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertUrgency.UNKNOWN,
            com.sysadmindoc.nimbus.data.model.AlertUrgency.from(null))
    }
}
