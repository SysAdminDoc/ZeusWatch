package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceConfigTest {

    @Test
    fun `selectedProviders collects every non-null slot`() {
        val config = SourceConfig(
            forecast = WeatherSourceProvider.OPEN_WEATHER_MAP,
            forecastFallback = WeatherSourceProvider.MET_NORWAY,
            alerts = WeatherSourceProvider.NWS,
            alertsFallback = null,
            airQuality = WeatherSourceProvider.OPEN_METEO,
            minutely = WeatherSourceProvider.OPEN_METEO,
        )

        val providers = config.selectedProviders()

        assertTrue(WeatherSourceProvider.OPEN_WEATHER_MAP in providers)
        assertTrue(WeatherSourceProvider.MET_NORWAY in providers)
        assertTrue(WeatherSourceProvider.NWS in providers)
        assertTrue(WeatherSourceProvider.OPEN_METEO in providers)
        // Null fallback slot contributes nothing.
        assertEquals(4, providers.size)
    }

    @Test
    fun `registry requiresApiKey identifies the keyed providers in use`() {
        val config = SourceConfig(forecast = WeatherSourceProvider.PIRATE_WEATHER)
        val keyed = config.selectedProviders().filter { it.requiresApiKey }.toSet()

        assertTrue(WeatherSourceProvider.PIRATE_WEATHER in keyed)
        assertFalse(WeatherSourceProvider.OPEN_METEO in keyed)
        assertFalse(WeatherSourceProvider.NWS in keyed)
    }

    @Test
    fun `no keyed providers when only no-key sources are selected`() {
        val config = SourceConfig(
            forecast = WeatherSourceProvider.OPEN_METEO,
            alerts = WeatherSourceProvider.MET_NORWAY,
        )
        assertTrue(config.selectedProviders().none { it.requiresApiKey })
    }

    @Test
    fun `only OpenWeatherMap and Pirate Weather require keys in the registry`() {
        val keyed = WeatherSourceProvider.entries.filter { it.requiresApiKey }.toSet()
        assertEquals(
            setOf(WeatherSourceProvider.OPEN_WEATHER_MAP, WeatherSourceProvider.PIRATE_WEATHER),
            keyed,
        )
    }
}
