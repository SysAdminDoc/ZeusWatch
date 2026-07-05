package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import java.io.ByteArrayInputStream
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTransferTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `settings backup does not serialize provider api keys`() {
        val backup = SettingsBackup(
            settings = NimbusSettings(
                tempUnit = TempUnit.CELSIUS,
                gadgetbridgeBroadcastEnabled = true,
                owmApiKey = "owm-secret",
                pirateWeatherApiKey = "pirate-secret",
                tempestAccessToken = "tempest-secret",
                tempestDeviceId = "62009",
            ).toBackup(),
        )

        val encoded = json.encodeToString(backup)

        assertFalse(encoded.contains("owm-secret"))
        assertFalse(encoded.contains("pirate-secret"))
        assertFalse(encoded.contains("tempest-secret"))
        assertFalse(encoded.contains("owmApiKey"))
        assertFalse(encoded.contains("pirateWeatherApiKey"))
        assertFalse(encoded.contains("tempestAccessToken"))
        assertTrue(encoded.contains("\"tempestDeviceId\":\"62009\""))
        assertTrue(encoded.contains(TempUnit.CELSIUS.name))
        assertTrue(encoded.contains("\"gadgetbridgeBroadcastEnabled\":true"))
    }

    @Test
    fun `settings backup preserves custom summary template`() {
        val template = "Start with {condition}, then mention {high} and {low}."
        val backup = SettingsBackup(
            settings = NimbusSettings(
                summaryStyle = SummaryStyle.CUSTOM_TEMPLATE,
                customSummaryTemplate = template,
            ).toBackup(),
        )

        val encoded = json.encodeToString(backup)
        val restored = backup.settings.toSettings()

        assertTrue(encoded.contains("customSummaryTemplate"))
        assertEquals(SummaryStyle.CUSTOM_TEMPLATE, restored.summaryStyle)
        assertEquals(template, restored.customSummaryTemplate)
    }

    @Test
    fun `backup reader rejects oversized imports before parsing`() {
        val oversized = ByteArray(MAX_SETTINGS_BACKUP_BYTES + 1) { '{'.code.toByte() }

        val result = runCatching {
            readSettingsBackupText(ByteArrayInputStream(oversized))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("import limit"))
    }

    @Test
    fun `imported settings sanitize invalid enum and range values`() {
        val restored = SettingsBackupPreferences(
            tempUnit = "KELVIN",
            windUnit = WindUnit.KNOTS.name,
            alertMinSeverity = "LOUD",
            cardOrder = listOf("DAILY_FORECAST", "BOGUS_CARD"),
            disabledCards = setOf("RADAR_PREVIEW", "BOGUS_CARD"),
            dailyBriefingMinutes = 99_999,
            hourlyForecastHours = 999,
            migrainePressureThreshold = -10.0,
            cacheTtlMinutes = 5,
            sourceForecast = "NOT_A_PROVIDER",
            gadgetbridgeBroadcastEnabled = true,
        ).toSettings()

        assertEquals(TempUnit.FAHRENHEIT, restored.tempUnit)
        assertEquals(WindUnit.KNOTS, restored.windUnit)
        assertEquals(AlertMinSeverity.SEVERE, restored.alertMinSeverity)
        assertEquals(CardType.DAILY_FORECAST, restored.cardOrder.first())
        assertTrue(CardType.RADAR_PREVIEW.name in restored.disabledCards)
        assertFalse("BOGUS_CARD" in restored.disabledCards)
        assertEquals(23 * 60 + 59, restored.dailyBriefingMinutes)
        assertEquals(72, restored.hourlyForecastHours)
        assertEquals(1.0, restored.migrainePressureThreshold, 0.0)
        assertEquals(15, restored.cacheTtlMinutes)
        assertEquals(
            WeatherSourceProvider.defaultFor(WeatherDataType.FORECAST),
            restored.sourceConfig.forecast,
        )
        assertTrue(restored.gadgetbridgeBroadcastEnabled)
    }

    @Test
    fun `saved location import validates source overrides and clears ids`() {
        val entity = SettingsBackupLocation(
            name = "Seattle",
            region = "Washington",
            country = "US",
            latitude = 47.6062,
            longitude = -122.3321,
            sortOrder = 4,
            forecastSource = WeatherSourceProvider.OPEN_METEO.name,
            alertSource = WeatherSourceProvider.OPEN_METEO.name,
            timeZone = "America/Los_Angeles",
        ).toEntity()

        assertEquals(0L, entity.id)
        assertEquals("Seattle", entity.name)
        assertEquals(4, entity.sortOrder)
        assertEquals(WeatherSourceProvider.OPEN_METEO.name, entity.forecastSource)
        assertNull(entity.alertSource)
        assertEquals("America/Los_Angeles", entity.timeZone)
    }

    @Test
    fun `imported unavailable KMA sources fall back with preview warning`() {
        val raw = json.encodeToString(
            SettingsBackup(
                settings = SettingsBackupPreferences(
                    sourceForecast = WeatherSourceProvider.OPEN_METEO_KMA.name,
                    sourceForecastFallback = WeatherSourceProvider.OPEN_METEO_KMA.name,
                ),
                savedLocations = listOf(
                    SettingsBackupLocation(
                        name = "Seoul",
                        country = "KR",
                        latitude = 37.5665,
                        longitude = 126.9780,
                        forecastSource = WeatherSourceProvider.OPEN_METEO_KMA.name,
                    ),
                ),
            ),
        )

        val preview = previewSettingsImport(raw)
        val restored = SettingsBackupPreferences(
            sourceForecast = WeatherSourceProvider.OPEN_METEO_KMA.name,
            sourceForecastFallback = WeatherSourceProvider.OPEN_METEO_KMA.name,
        ).toSettings()
        val entity = SettingsBackupLocation(
            name = "Seoul",
            latitude = 37.5665,
            longitude = 126.9780,
            forecastSource = WeatherSourceProvider.OPEN_METEO_KMA.name,
        ).toEntity()

        assertTrue(SettingsImportWarning.UNAVAILABLE_SOURCES in preview.warnings)
        assertEquals(WeatherSourceProvider.OPEN_METEO, restored.sourceConfig.forecast)
        assertNull(restored.sourceConfig.forecastFallback)
        assertNull(entity.forecastSource)
    }

    @Test
    fun `saved location export preserves restored ordering metadata`() {
        val backup = SavedLocationEntity(
            id = 42,
            name = "My Location",
            latitude = 39.7,
            longitude = -104.9,
            sortOrder = -1,
            isCurrentLocation = true,
            forecastSource = WeatherSourceProvider.MET_NORWAY.name,
            timeZone = "America/Denver",
        ).toBackup()

        assertEquals("My Location", backup.name)
        assertEquals(-1, backup.sortOrder)
        assertTrue(backup.isCurrentLocation)
        assertEquals(WeatherSourceProvider.MET_NORWAY.name, backup.forecastSource)
        assertEquals("America/Denver", backup.timeZone)
    }

    @Test
    fun `import preview rejects malformed json`() {
        val result = runCatching { previewSettingsImport("{not json") }

        assertTrue(result.isFailure)
    }

    @Test
    fun `import preview rejects future schema version`() {
        val raw = json.encodeToString(
            SettingsBackup(
                schemaVersion = 99,
                settings = NimbusSettings().toBackup(),
            ),
        )

        val result = runCatching { previewSettingsImport(raw) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Unsupported settings backup version 99"))
    }

    @Test
    fun `import preview rejects excessive saved locations`() {
        val raw = json.encodeToString(
            SettingsBackup(
                settings = NimbusSettings().toBackup(),
                savedLocations = List(251) { index ->
                    SettingsBackupLocation(
                        name = "Location $index",
                        latitude = 35.0 + index / 10_000.0,
                        longitude = -90.0,
                    )
                },
            ),
        )

        val result = runCatching { previewSettingsImport(raw) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("too many saved locations"))
    }

    @Test
    fun `import preview reports skipped blank and duplicate saved locations`() {
        val raw = json.encodeToString(
            SettingsBackup(
                settings = NimbusSettings().toBackup(),
                savedLocations = listOf(
                    SettingsBackupLocation(
                        name = "Denver",
                        region = "Colorado",
                        country = "US",
                        latitude = 39.7392,
                        longitude = -104.9903,
                    ),
                    SettingsBackupLocation(
                        name = "denver",
                        region = "colorado",
                        country = "us",
                        latitude = 39.73921,
                        longitude = -104.99029,
                    ),
                    SettingsBackupLocation(
                        name = "",
                        latitude = 47.6062,
                        longitude = -122.3321,
                    ),
                    SettingsBackupLocation(
                        name = "Invalid",
                        latitude = 95.0,
                        longitude = -122.3321,
                    ),
                ),
                customAlerts = emptyList(),
            ),
        )

        val preview = previewSettingsImport(raw, currentSavedLocationCount = 4)

        assertEquals(4, preview.currentSavedLocationCount)
        assertEquals(1, preview.savedLocationCount)
        assertEquals(1, preview.skippedLocationCount)
        assertEquals(1, preview.invalidLocationCount)
        assertEquals(1, preview.duplicateLocationCount)
        assertTrue(SettingsImportWarning.BLANK_LOCATION_NAMES in preview.warnings)
        assertTrue(SettingsImportWarning.INVALID_COORDINATES in preview.warnings)
        assertTrue(SettingsImportWarning.DUPLICATE_LOCATIONS in preview.warnings)
    }

    @Test
    fun `import rolls back snapshot when location replacement fails`() = runTest {
        val prefs = mockk<UserPreferences>(relaxed = true)
        val locationRepository = mockk<LocationRepository>()
        val originalLocation = SavedLocationEntity(
            id = 7,
            name = "Original",
            latitude = 35.0,
            longitude = -90.0,
        )
        every { prefs.settings } returns flowOf(NimbusSettings(tempUnit = TempUnit.CELSIUS))
        every { prefs.customAlertRules } returns flowOf(emptyList())
        every { prefs.lastLocation } returns flowOf(null)
        coEvery { locationRepository.getAll() } returns listOf(originalLocation)
        coEvery { locationRepository.replaceAll(any()) } throws IllegalStateException("replace failed")
        coEvery { locationRepository.restoreAll(any()) } returns Unit

        val raw = json.encodeToString(
            SettingsBackup(
                settings = NimbusSettings(tempUnit = TempUnit.FAHRENHEIT).toBackup(),
                savedLocations = listOf(
                    SettingsBackupLocation(
                        name = "Imported",
                        latitude = 40.0,
                        longitude = -105.0,
                    ),
                ),
                lastLocation = SettingsBackupLastLocation(
                    latitude = 40.0,
                    longitude = -105.0,
                    name = "Imported",
                ),
            ),
        )
        val manager = SettingsTransferManager(prefs, locationRepository)

        val result = runCatching { manager.importJson(raw) }

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        coVerify {
            locationRepository.restoreAll(
                match { restored ->
                    restored.single().id == 7L && restored.single().name == "Original"
                },
            )
            prefs.clearLastLocation()
        }
    }
}
