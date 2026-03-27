package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for NimbusSettings defaults, CardType parsing, and enum safety.
 *
 * Since UserPreferences is tightly coupled to Android DataStore (Context-based),
 * these tests focus on the pure-logic portions: NimbusSettings defaults,
 * card order parsing logic, and safe enum valueOf behavior.
 */
class UserPreferencesTest {

    // --- NimbusSettings defaults ---

    @Test
    fun defaultSettingsHaveCorrectUnitDefaults() {
        val settings = NimbusSettings()

        assertEquals(TempUnit.FAHRENHEIT, settings.tempUnit)
        assertEquals(WindUnit.MPH, settings.windUnit)
        assertEquals(PressureUnit.INHG, settings.pressureUnit)
        assertEquals(PrecipUnit.INCHES, settings.precipUnit)
        assertEquals(VisibilityUnit.MILES, settings.visibilityUnit)
        assertEquals(TimeFormat.TWELVE_HOUR, settings.timeFormat)
    }

    @Test
    fun defaultSettingsHaveCorrectAlertDefaults() {
        val settings = NimbusSettings()

        assertTrue(settings.alertNotificationsEnabled)
        assertEquals(AlertMinSeverity.SEVERE, settings.alertMinSeverity)
        assertFalse(settings.alertCheckAllLocations)
        assertEquals(AlertSourcePreference.AUTO, settings.alertSourcePref)
    }

    @Test
    fun defaultSettingsHaveCorrectDisplayDefaults() {
        val settings = NimbusSettings()

        assertEquals(RadarProvider.WINDY_WEBVIEW, settings.radarProvider)
        assertEquals(IconStyle.MATERIAL, settings.iconStyle)
        assertEquals("", settings.customIconPackId)
        assertEquals(ThemeMode.STATIC_DARK, settings.themeMode)
        assertEquals(SummaryStyle.TEMPLATE, settings.summaryStyle)
    }

    @Test
    fun defaultSettingsHaveCorrectNotificationDefaults() {
        val settings = NimbusSettings()

        assertFalse(settings.persistentWeatherNotif)
        assertFalse(settings.nowcastingAlerts)
        assertFalse(settings.drivingAlerts)
        assertFalse(settings.healthAlertsEnabled)
        assertFalse(settings.migraineAlerts)
    }

    @Test
    fun defaultSettingsHaveCorrectDataDisplayDefaults() {
        val settings = NimbusSettings()

        assertTrue(settings.showSnowfall)
        assertTrue(settings.showCape)
        assertTrue(settings.showSunshineDuration)
        assertTrue(settings.showGoldenHour)
        assertTrue(settings.showBeaufortColors)
        assertTrue(settings.showOutdoorScore)
        assertTrue(settings.showYesterdayComparison)
    }

    @Test
    fun defaultSettingsHaveCorrectCacheAndForecastDefaults() {
        val settings = NimbusSettings()

        assertEquals(48, settings.hourlyForecastHours)
        assertEquals(30, settings.cacheTtlMinutes)
        assertEquals(5.0, settings.migrainePressureThreshold, 0.01)
        assertTrue(settings.hapticFeedbackForAlerts)
    }

    @Test
    fun cacheTtlMsCalculatesCorrectly() {
        val settings = NimbusSettings(cacheTtlMinutes = 15)
        assertEquals(15 * 60 * 1000L, settings.cacheTtlMs)
    }

    @Test
    fun cacheTtlMsDefaultIs30Minutes() {
        val settings = NimbusSettings()
        assertEquals(30 * 60 * 1000L, settings.cacheTtlMs)
    }

    // --- SourceConfig defaults ---

    @Test
    fun defaultSourceConfigUsesOpenMeteoAndNws() {
        val settings = NimbusSettings()

        assertEquals(WeatherSourceProvider.OPEN_METEO, settings.sourceConfig.forecast)
        assertNull(settings.sourceConfig.forecastFallback)
        assertEquals(WeatherSourceProvider.NWS, settings.sourceConfig.alerts)
        assertNull(settings.sourceConfig.alertsFallback)
        assertEquals(WeatherSourceProvider.OPEN_METEO, settings.sourceConfig.airQuality)
        assertEquals(WeatherSourceProvider.OPEN_METEO, settings.sourceConfig.minutely)
    }

    // --- CardType enum ---

    @Test
    fun cardTypeEntriesContainAllExpectedValues() {
        val entries = CardType.entries
        assertTrue(entries.contains(CardType.WEATHER_SUMMARY))
        assertTrue(entries.contains(CardType.HOURLY_FORECAST))
        assertTrue(entries.contains(CardType.DAILY_FORECAST))
        assertTrue(entries.contains(CardType.RADAR_PREVIEW))
        assertTrue(entries.contains(CardType.DETAILS_GRID))
    }

    @Test
    fun defaultCardOrderContainsAllCardTypes() {
        assertEquals(CardType.entries.toList(), DEFAULT_CARD_ORDER)
    }

    @Test
    fun cardOrderParsingHandlesValidCommaSeparatedString() {
        val orderStr = "HOURLY_FORECAST,DAILY_FORECAST,WEATHER_SUMMARY"
        val parsed = orderStr.split(",").mapNotNull { name ->
            try { CardType.valueOf(name) } catch (_: Exception) { null }
        }

        assertEquals(3, parsed.size)
        assertEquals(CardType.HOURLY_FORECAST, parsed[0])
        assertEquals(CardType.DAILY_FORECAST, parsed[1])
        assertEquals(CardType.WEATHER_SUMMARY, parsed[2])
    }

    @Test
    fun cardOrderParsingSkipsInvalidEntriesGracefully() {
        val orderStr = "HOURLY_FORECAST,BOGUS_CARD,DAILY_FORECAST"
        val parsed = orderStr.split(",").mapNotNull { name ->
            try { CardType.valueOf(name) } catch (_: Exception) { null }
        }

        assertEquals(2, parsed.size)
        assertEquals(CardType.HOURLY_FORECAST, parsed[0])
        assertEquals(CardType.DAILY_FORECAST, parsed[1])
    }

    @Test
    fun cardOrderParsingReturnsDefaultForEmptyString() {
        val orderStr = ""
        val result = if (orderStr.isBlank()) DEFAULT_CARD_ORDER
        else orderStr.split(",").mapNotNull { name ->
            try { CardType.valueOf(name) } catch (_: Exception) { null }
        }

        assertEquals(DEFAULT_CARD_ORDER, result)
    }

    @Test
    fun cardOrderParsingAddsMissingCardsAtEnd() {
        val partialOrder = listOf(CardType.HOURLY_FORECAST, CardType.DAILY_FORECAST)
        val missing = DEFAULT_CARD_ORDER.filter { it !in partialOrder }
        val combined = partialOrder + missing

        // First two are our specified order
        assertEquals(CardType.HOURLY_FORECAST, combined[0])
        assertEquals(CardType.DAILY_FORECAST, combined[1])
        // All card types are present
        assertEquals(CardType.entries.size, combined.size)
        assertTrue(combined.containsAll(CardType.entries))
    }

    // --- TempUnit enum ---

    @Test
    fun tempUnitValuesAreParseable() {
        assertEquals(TempUnit.FAHRENHEIT, TempUnit.valueOf("FAHRENHEIT"))
        assertEquals(TempUnit.CELSIUS, TempUnit.valueOf("CELSIUS"))
    }

    @Test
    fun tempUnitHasCorrectSymbols() {
        assertEquals("\u00B0F", TempUnit.FAHRENHEIT.symbol)
        assertEquals("\u00B0C", TempUnit.CELSIUS.symbol)
    }

    // --- WindUnit enum ---

    @Test
    fun windUnitValuesAreParseable() {
        assertEquals(WindUnit.MPH, WindUnit.valueOf("MPH"))
        assertEquals(WindUnit.KMH, WindUnit.valueOf("KMH"))
        assertEquals(WindUnit.MS, WindUnit.valueOf("MS"))
        assertEquals(WindUnit.KNOTS, WindUnit.valueOf("KNOTS"))
    }

    // --- AlertMinSeverity enum ---

    @Test
    fun alertMinSeverityHasCorrectSortOrders() {
        assertEquals(0, AlertMinSeverity.EXTREME.maxSortOrder)
        assertEquals(1, AlertMinSeverity.SEVERE.maxSortOrder)
        assertEquals(2, AlertMinSeverity.MODERATE.maxSortOrder)
        assertEquals(4, AlertMinSeverity.ALL.maxSortOrder)
    }

    // --- Safe valueOf behavior (mirrors safeValueOf logic) ---

    @Test
    fun safeValueOfReturnsNullForInvalidEnumString() {
        // Mirrors the private safeValueOf function logic
        val result: TempUnit? = try {
            enumValueOf<TempUnit>("INVALID_VALUE")
        } catch (_: IllegalArgumentException) {
            null
        }
        assertNull(result)
    }

    @Test
    fun safeValueOfReturnsCorrectEnumForValidString() {
        val result: TempUnit? = try {
            enumValueOf<TempUnit>("CELSIUS")
        } catch (_: IllegalArgumentException) {
            null
        }
        assertEquals(TempUnit.CELSIUS, result)
    }

    @Test
    fun weatherSourceProviderSafeValueOfHandlesInvalidInput() {
        val result: WeatherSourceProvider? = try {
            enumValueOf<WeatherSourceProvider>("NONEXISTENT_PROVIDER")
        } catch (_: IllegalArgumentException) {
            null
        }
        assertNull(result)
    }

    @Test
    fun weatherSourceProviderSafeValueOfParsesValidInput() {
        val result: WeatherSourceProvider? = try {
            enumValueOf<WeatherSourceProvider>("OPEN_METEO")
        } catch (_: IllegalArgumentException) {
            null
        }
        assertEquals(WeatherSourceProvider.OPEN_METEO, result)
    }

    // --- SavedLocation ---

    @Test
    fun savedLocationStoresCorrectValues() {
        val loc = SavedLocation(latitude = 40.0, longitude = -105.0, name = "Boulder")
        assertEquals(40.0, loc.latitude, 0.01)
        assertEquals(-105.0, loc.longitude, 0.01)
        assertEquals("Boulder", loc.name)
    }

    // --- NimbusSettings disabled cards ---

    @Test
    fun defaultDisabledCardsIsEmpty() {
        val settings = NimbusSettings()
        assertTrue(settings.disabledCards.isEmpty())
    }

    @Test
    fun disabledCardsCanBeSetToSpecificCardNames() {
        val settings = NimbusSettings(disabledCards = setOf("POLLEN", "MOON_PHASE"))
        assertEquals(2, settings.disabledCards.size)
        assertTrue(settings.disabledCards.contains("POLLEN"))
        assertTrue(settings.disabledCards.contains("MOON_PHASE"))
    }
}
