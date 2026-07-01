package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingResult
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
        assertFalse(settings.onboardingComplete)
    }

    @Test
    fun defaultSettingsHaveCorrectAlertDefaults() {
        val settings = NimbusSettings()

        assertTrue(settings.alertNotificationsEnabled)
        assertEquals(AlertMinSeverity.SEVERE, settings.alertMinSeverity)
        assertTrue(settings.alertCheckAllLocations)
        assertEquals(AlertSourcePreference.AUTO, settings.alertSourcePref)
    }

    @Test
    fun defaultSettingsHaveCorrectDisplayDefaults() {
        val settings = NimbusSettings()

        assertEquals(RadarProvider.WINDY_WEBVIEW, settings.radarProvider)
        assertEquals(IconStyle.METEOCONS, settings.iconStyle)
        assertEquals("", settings.customIconPackId)
        assertEquals(ThemeMode.STATIC_DARK, settings.themeMode)
        assertEquals(SummaryStyle.AI_GENERATED, settings.summaryStyle)
    }

    @Test
    fun radarProvidersExposeExpectedPlaybackCapabilities() {
        assertFalse(RadarProvider.WINDY_WEBVIEW.supportsNativePlayback)
        assertTrue(RadarProvider.NATIVE_MAPLIBRE.supportsNativePlayback)
        assertTrue(RadarProvider.LIBREWXR_NATIVE.supportsNativePlayback)
        assertFalse(RadarProvider.NWS_WEBVIEW.supportsNativePlayback)
        assertFalse(RadarProvider.NWS_STANDARD_WEBVIEW.supportsNativePlayback)
    }

    @Test
    fun defaultSettingsHaveCorrectNotificationDefaults() {
        val settings = NimbusSettings()

        assertTrue(settings.persistentWeatherNotif)
        assertTrue(settings.nowcastingAlerts)
        assertFalse(settings.dailyBriefingEnabled)
        assertEquals(DEFAULT_DAILY_BRIEFING_MINUTES, settings.dailyBriefingMinutes)
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

        assertEquals(72, settings.hourlyForecastHours)
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

    @Test
    fun mergeRecentLocationSearchesMovesDuplicateToFront() {
        val denver = recentResult(1, "Denver", 39.7, -104.9)
        val chicago = recentResult(2, "Chicago", 41.8, -87.6)

        val result = mergeRecentLocationSearches(
            current = listOf(denver, chicago),
            result = denver.copy(name = "Denver, CO"),
        )

        assertEquals(listOf("Denver, CO", "Chicago"), result.map { it.name })
    }

    @Test
    fun mergeRecentLocationSearchesCapsNewestFirst() {
        val current = (1L..12L).map { id -> recentResult(id, "City $id", id.toDouble(), -id.toDouble()) }
        val newest = recentResult(100, "New City", 1.0, 2.0)

        val result = mergeRecentLocationSearches(current, newest, limit = 10)

        assertEquals(10, result.size)
        assertEquals("New City", result.first().name)
        assertFalse(result.any { it.name == "City 10" })
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

    @Test
    fun sourceConfigNormalizedFallsBackFromUnsupportedPrimarySelections() {
        // Pirate Weather doesn't support alerts; NWS doesn't support
        // air quality; OpenWeatherMap doesn't support minutely — all
        // three should normalize back to their safe defaults. ECCC
        // forecasts are now implemented, so the forecast primary is
        // allowed to remain ECCC.
        val normalized = SourceConfig(
            forecast = WeatherSourceProvider.ENVIRONMENT_CANADA,
            alerts = WeatherSourceProvider.PIRATE_WEATHER,
            airQuality = WeatherSourceProvider.NWS,
            minutely = WeatherSourceProvider.OPEN_WEATHER_MAP,
        ).normalized()

        assertEquals(WeatherSourceProvider.ENVIRONMENT_CANADA, normalized.forecast)
        assertEquals(WeatherSourceProvider.NWS, normalized.alerts)
        assertEquals(WeatherSourceProvider.OPEN_METEO, normalized.airQuality)
        assertEquals(WeatherSourceProvider.OPEN_METEO, normalized.minutely)
    }

    @Test
    fun sourceConfigNormalizedClearsDuplicateFallbacks() {
        // Duplicate primary/fallback still clears the fallback; ECCC as
        // a forecastFallback is now a valid selection so it's kept.
        val normalized = SourceConfig(
            forecast = WeatherSourceProvider.OPEN_METEO,
            forecastFallback = WeatherSourceProvider.ENVIRONMENT_CANADA,
            alerts = WeatherSourceProvider.NWS,
            alertsFallback = WeatherSourceProvider.NWS,
        ).normalized()

        assertEquals(
            WeatherSourceProvider.ENVIRONMENT_CANADA,
            normalized.forecastFallback,
        )
        assertNull(normalized.alertsFallback)
    }

    @Test
    fun weatherSourceProviderDefaultForReturnsSafeDefaults() {
        assertEquals(
            WeatherSourceProvider.OPEN_METEO,
            WeatherSourceProvider.defaultFor(WeatherDataType.FORECAST),
        )
        assertEquals(
            WeatherSourceProvider.NWS,
            WeatherSourceProvider.defaultFor(WeatherDataType.ALERTS),
        )
        assertEquals(
            WeatherSourceProvider.OPEN_METEO,
            WeatherSourceProvider.defaultFor(WeatherDataType.AIR_QUALITY),
        )
        assertEquals(
            WeatherSourceProvider.OPEN_METEO,
            WeatherSourceProvider.defaultFor(WeatherDataType.MINUTELY),
        )
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

    // --- parseCardOrder (shared by the settings flow and moveCardInOrder) ---

    @Test
    fun parseCardOrderReturnsDefaultForNullOrBlank() {
        assertEquals(DEFAULT_CARD_ORDER, parseCardOrder(null))
        assertEquals(DEFAULT_CARD_ORDER, parseCardOrder(""))
        assertEquals(DEFAULT_CARD_ORDER, parseCardOrder("  "))
    }

    @Test
    fun parseCardOrderSkipsUnknownNamesAndAppendsMissingCards() {
        val parsed = parseCardOrder("DAILY_FORECAST,BOGUS_CARD,HOURLY_FORECAST")

        assertEquals(CardType.DAILY_FORECAST, parsed[0])
        assertEquals(CardType.HOURLY_FORECAST, parsed[1])
        assertEquals(CardType.entries.size, parsed.size)
        assertTrue(parsed.containsAll(CardType.entries))
    }

    // --- moveCardInList (atomic card-reorder helper) ---

    @Test
    fun moveCardInListMovesUpAndDownByDelta() {
        val order = listOf(CardType.WEATHER_SUMMARY, CardType.HOURLY_FORECAST, CardType.DAILY_FORECAST)

        assertEquals(
            listOf(CardType.HOURLY_FORECAST, CardType.WEATHER_SUMMARY, CardType.DAILY_FORECAST),
            moveCardInList(order, CardType.HOURLY_FORECAST, -1),
        )
        assertEquals(
            listOf(CardType.WEATHER_SUMMARY, CardType.DAILY_FORECAST, CardType.HOURLY_FORECAST),
            moveCardInList(order, CardType.HOURLY_FORECAST, 1),
        )
    }

    @Test
    fun moveCardInListClampsAtTheListBounds() {
        val order = listOf(CardType.WEATHER_SUMMARY, CardType.HOURLY_FORECAST, CardType.DAILY_FORECAST)

        // Already first / last — no change.
        assertEquals(order, moveCardInList(order, CardType.WEATHER_SUMMARY, -1))
        assertEquals(order, moveCardInList(order, CardType.DAILY_FORECAST, 1))
        // Oversized delta clamps to the edge instead of throwing.
        assertEquals(
            listOf(CardType.HOURLY_FORECAST, CardType.DAILY_FORECAST, CardType.WEATHER_SUMMARY),
            moveCardInList(order, CardType.WEATHER_SUMMARY, 99),
        )
    }

    @Test
    fun moveCardInListIgnoresCardsNotInTheList() {
        val order = listOf(CardType.WEATHER_SUMMARY, CardType.HOURLY_FORECAST)
        assertEquals(order, moveCardInList(order, CardType.DAILY_FORECAST, 1))
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

    // --- Protobuf API key extraction (legacy encrypted DataStore migration) ---

    @Test
    fun extractApiKeysFromEmptyBytesReturnsEmptyKeys() {
        val keys = extractApiKeysFromPreferencesProto(ByteArray(0))
        assertEquals("", keys.owmApiKey)
        assertEquals("", keys.pirateWeatherApiKey)
    }

    @Test
    fun extractApiKeysFromGarbageBytesReturnsEmptyKeys() {
        val keys = extractApiKeysFromPreferencesProto(byteArrayOf(0xFF.toByte(), 0x01, 0x02))
        assertEquals("", keys.owmApiKey)
        assertEquals("", keys.pirateWeatherApiKey)
    }

    @Test
    fun extractApiKeysFromValidProtobufReturnsKeys() {
        val proto = buildPreferencesProto(
            "owm_api_key" to "owm-secret-123",
            "pirate_weather_api_key" to "pirate-secret-456",
        )
        val keys = extractApiKeysFromPreferencesProto(proto)
        assertEquals("owm-secret-123", keys.owmApiKey)
        assertEquals("pirate-secret-456", keys.pirateWeatherApiKey)
    }

    @Test
    fun extractApiKeysIgnoresUnrelatedEntries() {
        val proto = buildPreferencesProto(
            "some_other_key" to "value",
            "owm_api_key" to "my-owm-key",
        )
        val keys = extractApiKeysFromPreferencesProto(proto)
        assertEquals("my-owm-key", keys.owmApiKey)
        assertEquals("", keys.pirateWeatherApiKey)
    }

    /**
     * Build a minimal protobuf blob matching the DataStore Preferences
     * wire format: field 1 (LEN) = map entries with string key (1) and
     * Value message (2) containing string_value (5).
     */
    private fun buildPreferencesProto(vararg entries: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for ((key, value) in entries) {
            val valueMsg = buildLenDelimited(5, value.toByteArray())
            val entryMsg = buildLenDelimited(1, key.toByteArray()) + buildLenDelimited(2, valueMsg)
            out.write(buildLenDelimited(1, entryMsg))
        }
        return out.toByteArray()
    }

    private fun buildLenDelimited(fieldNumber: Int, payload: ByteArray): ByteArray {
        val tag = (fieldNumber shl 3) or 2
        val out = java.io.ByteArrayOutputStream()
        writeVarint(out, tag)
        writeVarint(out, payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeVarint(out: java.io.ByteArrayOutputStream, value: Int) {
        var v = value
        while (v > 0x7F) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
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
    fun defaultDisabledCardsMatchCuratedDefaults() {
        val settings = NimbusSettings()

        assertEquals(DEFAULT_DISABLED_CARDS, settings.disabledCards)
        assertTrue(settings.disabledCards.contains(CardType.OUTDOOR_SCORE.name))
        assertTrue(settings.disabledCards.contains(CardType.CLOTHING.name))
    }

    @Test
    fun disabledCardsCanBeSetToSpecificCardNames() {
        val settings = NimbusSettings(disabledCards = setOf("POLLEN", "MOON_PHASE"))
        assertEquals(2, settings.disabledCards.size)
        assertTrue(settings.disabledCards.contains("POLLEN"))
        assertTrue(settings.disabledCards.contains("MOON_PHASE"))
    }

    @Test
    fun starterCardSetsMapToExpectedDisabledCards() {
        val minimal = disabledCardsForStarterSet(StarterCardSet.MINIMAL)

        assertFalse(minimal.contains(CardType.WEATHER_SUMMARY.name))
        assertFalse(minimal.contains(CardType.HOURLY_FORECAST.name))
        assertFalse(minimal.contains(CardType.DAILY_FORECAST.name))
        assertTrue(minimal.contains(CardType.POLLEN.name))
        assertEquals(DEFAULT_DISABLED_CARDS, disabledCardsForStarterSet(StarterCardSet.STANDARD))
        assertTrue(disabledCardsForStarterSet(StarterCardSet.EVERYTHING).isEmpty())
    }

    @Test
    fun onboardingUnitBundlesFollowSelectedTemperatureSystem() {
        assertEquals(WindUnit.MPH, preferredWindUnitFor(TempUnit.FAHRENHEIT))
        assertEquals(PressureUnit.INHG, preferredPressureUnitFor(TempUnit.FAHRENHEIT))
        assertEquals(PrecipUnit.INCHES, preferredPrecipUnitFor(TempUnit.FAHRENHEIT))
        assertEquals(VisibilityUnit.MILES, preferredVisibilityUnitFor(TempUnit.FAHRENHEIT))

        assertEquals(WindUnit.KMH, preferredWindUnitFor(TempUnit.CELSIUS))
        assertEquals(PressureUnit.HPA, preferredPressureUnitFor(TempUnit.CELSIUS))
        assertEquals(PrecipUnit.MM, preferredPrecipUnitFor(TempUnit.CELSIUS))
        assertEquals(VisibilityUnit.KM, preferredVisibilityUnitFor(TempUnit.CELSIUS))
    }

    @Test
    fun dailyBriefingMinutesClampToOneDay() {
        assertEquals(0, normalizeDailyBriefingMinutes(-12))
        assertEquals(8 * 60, normalizeDailyBriefingMinutes(8 * 60))
        assertEquals(23 * 60 + 59, normalizeDailyBriefingMinutes(24 * 60 + 15))
    }
}

private fun recentResult(
    id: Long,
    name: String,
    latitude: Double,
    longitude: Double,
): GeocodingResult = GeocodingResult(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    country = "United States",
)
