package com.sysadmindoc.nimbus.ui.screen.settings

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelLogicTest {

    private fun location(
        name: String,
        forecastSource: String? = null,
        alertSource: String? = null,
    ) = SavedLocationEntity(
        name = name,
        latitude = 40.0,
        longitude = -105.0,
        forecastSource = forecastSource,
        alertSource = alertSource,
    )

    @Test
    fun noLocationsYieldsNoOverrideProviders() {
        assertEquals(emptySet<WeatherSourceProvider>(), savedLocationOverrideProviders(emptyList()))
    }

    @Test
    fun locationsWithoutOverridesYieldNoProviders() {
        val locations = listOf(location("Denver"), location("Boulder"))
        assertEquals(emptySet<WeatherSourceProvider>(), savedLocationOverrideProviders(locations))
    }

    @Test
    fun keyedProviderSelectedOnlyAsLocationOverrideIsSurfaced() {
        val locations = listOf(
            location("Denver", forecastSource = WeatherSourceProvider.PIRATE_WEATHER.name),
            location("Boulder", alertSource = WeatherSourceProvider.OPEN_WEATHER_MAP.name),
        )
        assertEquals(
            setOf(WeatherSourceProvider.PIRATE_WEATHER, WeatherSourceProvider.OPEN_WEATHER_MAP),
            savedLocationOverrideProviders(locations),
        )
    }

    @Test
    fun duplicateOverridesAreDeduplicated() {
        val locations = listOf(
            location("Denver", forecastSource = WeatherSourceProvider.PIRATE_WEATHER.name),
            location("Boulder", forecastSource = WeatherSourceProvider.PIRATE_WEATHER.name),
        )
        assertEquals(
            setOf(WeatherSourceProvider.PIRATE_WEATHER),
            savedLocationOverrideProviders(locations),
        )
    }

    @Test
    fun staleOrInvalidStoredOverridesAreDropped() {
        val locations = listOf(
            // Unknown stored name (e.g. provider removed in a later build).
            location("Denver", forecastSource = "NOT_A_PROVIDER"),
            // NWS is alerts-only, so it is not selectable as a forecast override.
            location("Boulder", forecastSource = WeatherSourceProvider.NWS.name),
            // OPEN_METEO_KMA is suspended (implementedTypes is empty).
            location("Golden", forecastSource = WeatherSourceProvider.OPEN_METEO_KMA.name),
        )
        assertEquals(emptySet<WeatherSourceProvider>(), savedLocationOverrideProviders(locations))
    }

    @Test
    fun keylessOverridesAreStillReportedForCallersToFilter() {
        // The flow reports every override provider; the API-key UI applies the
        // requiresApiKey filter itself.
        val locations = listOf(
            location("Berlin", forecastSource = WeatherSourceProvider.BRIGHT_SKY.name),
        )
        assertEquals(
            setOf(WeatherSourceProvider.BRIGHT_SKY),
            savedLocationOverrideProviders(locations),
        )
    }
}
