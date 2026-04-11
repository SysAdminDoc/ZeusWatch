package com.sysadmindoc.nimbus.data.model

import com.sysadmindoc.nimbus.data.api.GeocodingResult
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SavedLocationMatchingTest {

    private lateinit var originalLocale: Locale

    @Before
    fun saveLocale() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `matches by coordinate within epsilon`() {
        val saved = SavedLocationEntity(
            id = 1L,
            name = "Boulder",
            region = "Colorado",
            country = "United States",
            latitude = 40.01499,
            longitude = -105.2705,
            sortOrder = 0,
        )
        val result = GeocodingResult(
            id = 2L,
            name = "Boulder",
            latitude = 40.01503, // <0.0001 delta
            longitude = -105.27049,
            country = "United States",
            admin1 = "Colorado",
        )
        assertTrue(matchesSavedLocation(result, saved))
    }

    @Test
    fun `does not match the current-location entry even if coords are identical`() {
        val saved = SavedLocationEntity(
            id = 1L,
            name = "Denver",
            latitude = 39.7392,
            longitude = -104.9903,
            isCurrentLocation = true,
            sortOrder = -1,
        )
        val result = GeocodingResult(
            id = 2L,
            name = "Denver",
            latitude = 39.7392,
            longitude = -104.9903,
            country = "United States",
            admin1 = "Colorado",
        )
        // Users who add "Denver" while GPS reports Denver should get a pinned entry,
        // not have it silently eaten by the current-location record.
        assertFalse(matchesSavedLocation(result, saved))
    }

    @Test
    fun `label match strips diacritics so accented spellings still dedupe`() {
        // "París" vs "Paris" — a legitimate case where the Spanish-language
        // source and the English-language source return the same city with
        // different diacritics. NFD + combining-mark strip makes them equal.
        val saved = SavedLocationEntity(
            id = 1L,
            name = "Paris",
            region = "Île-de-France",
            country = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            sortOrder = 0,
        )
        val result = GeocodingResult(
            id = 2L,
            name = "París",
            latitude = 0.0, // distant, forces label path
            longitude = 0.0,
            country = "France",
            admin1 = "Ile-de-France", // note the ASCII spelling
        )
        assertTrue(matchesSavedLocation(result, saved))
    }

    @Test
    fun `label match is stable across device locales for Turkish İstanbul`() {
        // Reproduces the Turkish-i trap: on Turkish devices, default-locale
        // lowercase() turns "I" into "ı" (dotless-i), which means "Istanbul"
        // and "istanbul" would NOT compare equal in the naive impl. NFD +
        // ASCII-lowercase makes the answer identical regardless of device locale.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        val saved = SavedLocationEntity(
            id = 1L,
            name = "Istanbul",
            region = "Istanbul",
            country = "Turkey",
            latitude = 41.0082,
            longitude = 28.9784,
            sortOrder = 0,
        )
        val result = GeocodingResult(
            id = 2L,
            name = "istanbul",
            latitude = 0.0,
            longitude = 0.0,
            country = "Turkey",
            admin1 = "istanbul",
        )
        assertTrue(matchesSavedLocation(result, saved))
    }
}
