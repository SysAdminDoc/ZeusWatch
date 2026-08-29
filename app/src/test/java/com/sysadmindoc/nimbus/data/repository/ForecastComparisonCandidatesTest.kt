package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.ui.screen.compare.compareOverlayCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Compare overlay and provider agreement both used to carry their own
 * hardcoded list of Open-Meteo and MET Norway. Nothing broke when a provider
 * was added, which is why ECMWF AIFS shipped absent from both surfaces with no
 * failing test. These hold the candidate set to the registry.
 */
class ForecastComparisonCandidatesTest {

    @Test
    fun `every keyless global forecast source is a candidate`() {
        // Derived, so this is the assertion that fails the day someone
        // reintroduces a hand-written list: a new global provider would be in
        // the registry and missing from the candidates.
        val expected = WeatherSourceProvider.entries.filter {
            it.hasGlobalCoverage &&
                !it.requiresApiKey &&
                it.isSelectableFor(WeatherDataType.FORECAST)
        }
        val candidates = WeatherSourceProvider.forecastComparisonCandidates(
            primary = WeatherSourceProvider.OPEN_METEO,
            limit = Int.MAX_VALUE,
        )

        assertEquals(expected, candidates)
    }

    @Test
    fun `ECMWF AIFS is a candidate`() {
        // The provider whose absence produced this item.
        assertTrue(
            WeatherSourceProvider.OPEN_METEO_AIFS in
                WeatherSourceProvider.forecastComparisonCandidates(
                    primary = WeatherSourceProvider.OPEN_METEO,
                    limit = Int.MAX_VALUE,
                ),
        )
    }

    @Test
    fun `the primary source is always first even when it is regional`() {
        val candidates = WeatherSourceProvider.forecastComparisonCandidates(
            WeatherSourceProvider.BRIGHT_SKY,
        )

        assertEquals(WeatherSourceProvider.BRIGHT_SKY, candidates.first())
    }

    @Test
    fun `a regional source is never offered as the second opinion`() {
        // A German or Hong Kong forecaster has nothing useful to say about
        // Denver, and offering it as agreement would read as disagreement.
        val regional = WeatherSourceProvider.entries.filter {
            !it.hasGlobalCoverage && it.isSelectableFor(WeatherDataType.FORECAST)
        }
        assertTrue("expected regional forecast sources to exist", regional.isNotEmpty())

        val candidates = WeatherSourceProvider.forecastComparisonCandidates(
            primary = WeatherSourceProvider.OPEN_METEO,
            limit = Int.MAX_VALUE,
        )

        assertEquals(emptyList<WeatherSourceProvider>(), candidates.filter { it in regional })
    }

    @Test
    fun `a key-gated source is never a candidate`() {
        // The comparison has to work for someone who has not signed up
        // anywhere, so a provider that needs a key cannot be the fallback.
        val candidates = WeatherSourceProvider.forecastComparisonCandidates(
            primary = WeatherSourceProvider.OPEN_METEO,
            limit = Int.MAX_VALUE,
        )

        assertFalse(candidates.any { it.requiresApiKey })
    }

    @Test
    fun `an unimplemented source is never a candidate`() {
        val candidates = WeatherSourceProvider.forecastComparisonCandidates(
            primary = WeatherSourceProvider.OPEN_METEO,
            limit = Int.MAX_VALUE,
        )

        assertTrue(candidates.all { it.isSelectableFor(WeatherDataType.FORECAST) })
    }

    @Test
    fun `the primary appears once when it is already a global source`() {
        val candidates = WeatherSourceProvider.forecastComparisonCandidates(
            WeatherSourceProvider.MET_NORWAY,
        )

        assertEquals(candidates.size, candidates.distinct().size)
        assertEquals(WeatherSourceProvider.MET_NORWAY, candidates.first())
    }

    @Test
    fun `the compare overlay offers the same candidates as the registry`() {
        // Two surfaces, one rule: this fails if either grows its own list again.
        WeatherSourceProvider.entries
            .filter { it.isSelectableFor(WeatherDataType.FORECAST) }
            .forEach { primary ->
                assertEquals(
                    "compare overlay diverged for $primary",
                    WeatherSourceProvider.forecastComparisonCandidates(primary),
                    compareOverlayCandidates(primary),
                )
            }
    }
}
