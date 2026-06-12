package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                owmApiKey = "owm-secret",
                pirateWeatherApiKey = "pirate-secret",
            ).toBackup(),
        )

        val encoded = json.encodeToString(backup)

        assertFalse(encoded.contains("owm-secret"))
        assertFalse(encoded.contains("pirate-secret"))
        assertFalse(encoded.contains("owmApiKey"))
        assertFalse(encoded.contains("pirateWeatherApiKey"))
        assertTrue(encoded.contains(TempUnit.CELSIUS.name))
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
        ).toEntity()

        assertEquals(0L, entity.id)
        assertEquals("Seattle", entity.name)
        assertEquals(4, entity.sortOrder)
        assertEquals(WeatherSourceProvider.OPEN_METEO.name, entity.forecastSource)
        assertNull(entity.alertSource)
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
        ).toBackup()

        assertEquals("My Location", backup.name)
        assertEquals(-1, backup.sortOrder)
        assertTrue(backup.isCurrentLocation)
        assertEquals(WeatherSourceProvider.MET_NORWAY.name, backup.forecastSource)
    }
}
