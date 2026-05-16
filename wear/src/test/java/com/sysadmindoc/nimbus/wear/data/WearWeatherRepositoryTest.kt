package com.sysadmindoc.nimbus.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [WearWeatherRepository] companion helpers.
 *
 * These are pure functions used by the tile, complication, hourly screen,
 * and the [com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore] fallback
 * path when the synced condition string is missing. Drift here silently
 * mislabels weather on the watch, so the mapping is locked down per
 * Open-Meteo's WMO weather code spec.
 */
class WearWeatherRepositoryTest {

    @Test
    fun `wmoDescription maps clear sky and overcast bands`() {
        assertEquals("Clear Sky", WearWeatherRepository.wmoDescription(0))
        assertEquals("Mostly Clear", WearWeatherRepository.wmoDescription(1))
        assertEquals("Partly Cloudy", WearWeatherRepository.wmoDescription(2))
        assertEquals("Overcast", WearWeatherRepository.wmoDescription(3))
    }

    @Test
    fun `wmoDescription maps fog and freezing precipitation`() {
        assertEquals("Fog", WearWeatherRepository.wmoDescription(45))
        assertEquals("Fog", WearWeatherRepository.wmoDescription(48))
        assertEquals("Drizzle", WearWeatherRepository.wmoDescription(53))
        assertEquals("Freezing Drizzle", WearWeatherRepository.wmoDescription(56))
        assertEquals("Freezing Rain", WearWeatherRepository.wmoDescription(67))
    }

    @Test
    fun `wmoDescription maps rain, showers, and snow ranges`() {
        assertEquals("Rain", WearWeatherRepository.wmoDescription(61))
        assertEquals("Rain", WearWeatherRepository.wmoDescription(65))
        assertEquals("Snow", WearWeatherRepository.wmoDescription(71))
        assertEquals("Snow", WearWeatherRepository.wmoDescription(75))
        assertEquals("Snow Grains", WearWeatherRepository.wmoDescription(77))
        assertEquals("Showers", WearWeatherRepository.wmoDescription(80))
        assertEquals("Showers", WearWeatherRepository.wmoDescription(82))
        assertEquals("Snow Showers", WearWeatherRepository.wmoDescription(85))
    }

    @Test
    fun `wmoDescription maps thunderstorm and hail variants`() {
        assertEquals("Thunderstorm", WearWeatherRepository.wmoDescription(95))
        assertEquals("Thunderstorm + Hail", WearWeatherRepository.wmoDescription(96))
        assertEquals("Thunderstorm + Hail", WearWeatherRepository.wmoDescription(99))
    }

    @Test
    fun `wmoDescription falls back to Unknown for unsupported codes`() {
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(-1))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(4))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(50))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(100))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(Int.MAX_VALUE))
    }

    @Test
    fun `wmoEmoji distinguishes day and night for clear and mostly clear`() {
        // U+2600 sun for day clear, U+1F319 crescent moon for night clear.
        assertEquals("☀️", WearWeatherRepository.wmoEmoji(0, isDay = true))
        assertEquals("🌙", WearWeatherRepository.wmoEmoji(0, isDay = false))
        // U+1F324 sun behind small cloud for day mostly clear.
        assertEquals("🌤️", WearWeatherRepository.wmoEmoji(1, isDay = true))
        assertEquals("🌙", WearWeatherRepository.wmoEmoji(1, isDay = false))
    }

    @Test
    fun `wmoEmoji uses isDay-agnostic glyphs for overcast and precipitation`() {
        val cloudy = WearWeatherRepository.wmoEmoji(2, isDay = true)
        assertEquals(cloudy, WearWeatherRepository.wmoEmoji(2, isDay = false))
        val overcast = WearWeatherRepository.wmoEmoji(3, isDay = true)
        assertEquals(overcast, WearWeatherRepository.wmoEmoji(3, isDay = false))
        val rain = WearWeatherRepository.wmoEmoji(63, isDay = true)
        assertEquals(rain, WearWeatherRepository.wmoEmoji(63, isDay = false))
        val snow = WearWeatherRepository.wmoEmoji(73, isDay = true)
        assertEquals(snow, WearWeatherRepository.wmoEmoji(73, isDay = false))
    }

    @Test
    fun `wmoEmoji maps thunderstorm range to thunder cloud glyph`() {
        // U+26C8 thunder cloud and rain.
        val expected = "⛈️"
        assertEquals(expected, WearWeatherRepository.wmoEmoji(95))
        assertEquals(expected, WearWeatherRepository.wmoEmoji(96))
        assertEquals(expected, WearWeatherRepository.wmoEmoji(99))
    }

    @Test
    fun `wmoEmoji fog range uses fog glyph regardless of day or night`() {
        val expected = "🌫️"
        assertEquals(expected, WearWeatherRepository.wmoEmoji(45, isDay = true))
        assertEquals(expected, WearWeatherRepository.wmoEmoji(48, isDay = false))
    }

    @Test
    fun `wmoEmoji falls back to thermometer for unknown codes`() {
        // U+1F321 thermometer for unknowns so the user still sees a glyph.
        val fallback = "🌡️"
        assertEquals(fallback, WearWeatherRepository.wmoEmoji(-1))
        assertEquals(fallback, WearWeatherRepository.wmoEmoji(100))
        assertEquals(fallback, WearWeatherRepository.wmoEmoji(Int.MAX_VALUE))
    }
}
