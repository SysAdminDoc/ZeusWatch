package com.sysadmindoc.nimbus.data.model

import org.junit.Assert.*
import org.junit.Test

class WeatherCodeTest {

    @Test
    fun `fromCode returns correct enum for valid WMO codes`() {
        assertEquals(WeatherCode.CLEAR_SKY, WeatherCode.fromCode(0))
        assertEquals(WeatherCode.OVERCAST, WeatherCode.fromCode(3))
        assertEquals(WeatherCode.FOG, WeatherCode.fromCode(45))
        assertEquals(WeatherCode.RAIN_MODERATE, WeatherCode.fromCode(63))
        assertEquals(WeatherCode.SNOW_HEAVY, WeatherCode.fromCode(75))
        assertEquals(WeatherCode.THUNDERSTORM, WeatherCode.fromCode(95))
    }

    @Test
    fun `fromCode returns UNKNOWN for null`() {
        assertEquals(WeatherCode.UNKNOWN, WeatherCode.fromCode(null))
    }

    @Test
    fun `fromCode returns UNKNOWN for unmapped code`() {
        assertEquals(WeatherCode.UNKNOWN, WeatherCode.fromCode(999))
        assertEquals(WeatherCode.UNKNOWN, WeatherCode.fromCode(-99))
    }

    @Test
    fun `isRainy true for rain codes`() {
        assertTrue(WeatherCode.DRIZZLE_LIGHT.isRainy)
        assertTrue(WeatherCode.RAIN_MODERATE.isRainy)
        assertTrue(WeatherCode.RAIN_SHOWERS_VIOLENT.isRainy)
        assertTrue(WeatherCode.FREEZING_RAIN_LIGHT.isRainy)
    }

    @Test
    fun `isRainy false for non-rain codes`() {
        assertFalse(WeatherCode.CLEAR_SKY.isRainy)
        assertFalse(WeatherCode.SNOW_MODERATE.isRainy)
        assertFalse(WeatherCode.THUNDERSTORM.isRainy)
    }

    @Test
    fun `isSnowy true for snow codes`() {
        assertTrue(WeatherCode.SNOW_SLIGHT.isSnowy)
        assertTrue(WeatherCode.SNOW_HEAVY.isSnowy)
        assertTrue(WeatherCode.SNOW_GRAINS.isSnowy)
        assertTrue(WeatherCode.SNOW_SHOWERS_SLIGHT.isSnowy)
    }

    @Test
    fun `isSnowy false for non-snow codes`() {
        assertFalse(WeatherCode.CLEAR_SKY.isSnowy)
        assertFalse(WeatherCode.RAIN_HEAVY.isSnowy)
    }

    @Test
    fun `isStormy true for thunderstorm codes`() {
        assertTrue(WeatherCode.THUNDERSTORM.isStormy)
        assertTrue(WeatherCode.THUNDERSTORM_HAIL_SLIGHT.isStormy)
        assertTrue(WeatherCode.THUNDERSTORM_HAIL_HEAVY.isStormy)
    }

    @Test
    fun `isStormy false for non-storm codes`() {
        assertFalse(WeatherCode.RAIN_HEAVY.isStormy)
        assertFalse(WeatherCode.SNOW_HEAVY.isStormy)
    }

    @Test
    fun `iconName returns day icon when isDay true`() {
        assertEquals("clear_day", WeatherCode.CLEAR_SKY.iconName(isDay = true))
        assertEquals("partly_cloudy_day", WeatherCode.PARTLY_CLOUDY.iconName(isDay = true))
    }

    @Test
    fun `iconName returns night icon when isDay false`() {
        assertEquals("clear_night", WeatherCode.CLEAR_SKY.iconName(isDay = false))
        assertEquals("partly_cloudy_night", WeatherCode.PARTLY_CLOUDY.iconName(isDay = false))
    }

    @Test
    fun `overcast icon same for day and night`() {
        assertEquals("overcast", WeatherCode.OVERCAST.iconName(isDay = true))
        assertEquals("overcast", WeatherCode.OVERCAST.iconName(isDay = false))
    }

    @Test
    fun `all entries have unique codes except UNKNOWN`() {
        val codes = WeatherCode.entries.filter { it != WeatherCode.UNKNOWN }.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `all entries have non-blank descriptions`() {
        WeatherCode.entries.forEach { code ->
            assertTrue("${code.name} has blank description", code.description.isNotBlank())
        }
    }
}
