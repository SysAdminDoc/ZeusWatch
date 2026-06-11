package com.sysadmindoc.nimbus.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [WearUnitFormatter] — the only place the watch converts
 * canonical metric values to display units. A regression here shows every
 * imperial user Celsius/km/h mislabeled as their chosen units.
 */
class WearUnitFormatterTest {

    @Test
    fun `displayTemp converts celsius to fahrenheit with rounding`() {
        assertEquals(32, WearUnitFormatter.displayTemp(0, WearUnitFormatter.TEMP_FAHRENHEIT))
        assertEquals(72, WearUnitFormatter.displayTemp(22, WearUnitFormatter.TEMP_FAHRENHEIT))
        assertEquals(-40, WearUnitFormatter.displayTemp(-40, WearUnitFormatter.TEMP_FAHRENHEIT))
        assertEquals(99, WearUnitFormatter.displayTemp(37, WearUnitFormatter.TEMP_FAHRENHEIT))
    }

    @Test
    fun `displayTemp passes celsius through for metric or unknown units`() {
        assertEquals(22, WearUnitFormatter.displayTemp(22, WearUnitFormatter.TEMP_CELSIUS))
        assertEquals(22, WearUnitFormatter.displayTemp(22, ""))
        assertEquals(22, WearUnitFormatter.displayTemp(22, "KELVIN"))
    }

    @Test
    fun `displayWind converts kmh to the selected unit`() {
        assertEquals(10, WearUnitFormatter.displayWind(16, WearUnitFormatter.WIND_MPH))
        assertEquals(10, WearUnitFormatter.displayWind(36, WearUnitFormatter.WIND_MS))
        assertEquals(10, WearUnitFormatter.displayWind(19, WearUnitFormatter.WIND_KNOTS))
        assertEquals(16, WearUnitFormatter.displayWind(16, WearUnitFormatter.WIND_KMH))
        // Unknown unit strings fall back to metric pass-through.
        assertEquals(16, WearUnitFormatter.displayWind(16, "FURLONGS"))
    }

    @Test
    fun `windLabel matches the selected unit`() {
        assertEquals("mph", WearUnitFormatter.windLabel(WearUnitFormatter.WIND_MPH))
        assertEquals("m/s", WearUnitFormatter.windLabel(WearUnitFormatter.WIND_MS))
        assertEquals("kn", WearUnitFormatter.windLabel(WearUnitFormatter.WIND_KNOTS))
        assertEquals("km/h", WearUnitFormatter.windLabel(WearUnitFormatter.WIND_KMH))
        assertEquals("km/h", WearUnitFormatter.windLabel(""))
    }
}
