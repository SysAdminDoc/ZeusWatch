package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.BmkgAlertAdapter
import com.sysadmindoc.nimbus.data.api.GeoSphereAustriaAlertAdapter
import com.sysadmindoc.nimbus.data.api.GeoSphereAustriaNowcastAdapter
import com.sysadmindoc.nimbus.data.model.*
import com.sysadmindoc.nimbus.di.WeatherSourceAdapterModule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherSourceManagerTest {

    private lateinit var prefs: UserPreferences
    private lateinit var openMeteoAdapter: OpenMeteoForecastAdapter
    private lateinit var openMeteoBomAdapter: OpenMeteoBomForecastAdapter
    private lateinit var openMeteoKmaAdapter: OpenMeteoKmaForecastAdapter
    private lateinit var openMeteoUkmoAdapter: OpenMeteoUkmoForecastAdapter
    private lateinit var openMeteoDmiAdapter: OpenMeteoDmiForecastAdapter
    private lateinit var openMeteoMeteoFranceAdapter: OpenMeteoMeteoFranceForecastAdapter
    private lateinit var openMeteoMinutelyAdapter: OpenMeteoMinutelyAdapter
    private lateinit var openMeteoMeteoFranceMinutelyAdapter: OpenMeteoMeteoFranceMinutelyAdapter
    private lateinit var alertAdapter: AlertSourceManagerAdapter
    private lateinit var aqiAdapter: OpenMeteoAqiAdapter
    private lateinit var owmForecastAdapter: OwmForecastAdapter
    private lateinit var owmAlertAdapter: OwmAlertAdapter
    private lateinit var owmAqiAdapter: OwmAqiAdapter
    private lateinit var pirateWeatherAdapter: PirateWeatherForecastAdapter
    private lateinit var brightSkyForecastAdapter: BrightSkyForecastAdapter
    private lateinit var brightSkyAlertAdapter: BrightSkyAlertAdapter
    private lateinit var metNorwayForecastAdapter: MetNorwayForecastAdapter
    private lateinit var ecccForecastAdapter: EnvironmentCanadaForecastAdapter
    private lateinit var hkoForecastAdapter: HkoForecastAdapter
    private lateinit var hkoAlertAdapter: HkoAlertAdapter
    private lateinit var bmkgAlertAdapter: BmkgAlertAdapter
    private lateinit var geoSphereAustriaAlertAdapter: GeoSphereAustriaAlertAdapter
    private lateinit var geoSphereAustriaNowcastAdapter: GeoSphereAustriaNowcastAdapter
    private lateinit var timeZoneResolver: LocationTimeZoneResolver
    private lateinit var providerHealthRepository: ProviderHealthRepository
    private lateinit var manager: WeatherSourceManager

    private val testWeatherData = WeatherData(
        location = LocationInfo(name = "Test City", latitude = 40.0, longitude = -74.0),
        current = CurrentConditions(
            temperature = 20.0, feelsLike = 18.0, humidity = 50,
            weatherCode = WeatherCode.CLEAR_SKY, isDay = true,
            windSpeed = 10.0, windDirection = 180, windGusts = null,
            pressure = 1013.0, uvIndex = 3.0, visibility = 15000.0,
            dewPoint = 10.0, cloudCover = 20, precipitation = 0.0,
            dailyHigh = 25.0, dailyLow = 15.0, sunrise = "06:00", sunset = "18:00",
        ),
        hourly = emptyList(),
        daily = emptyList(),
    )

    private val defaultSettings = NimbusSettings(
        sourceConfig = SourceConfig(
            forecast = WeatherSourceProvider.OPEN_METEO,
            forecastFallback = null,
            alerts = WeatherSourceProvider.NWS,
            alertsFallback = null,
            airQuality = WeatherSourceProvider.OPEN_METEO,
            minutely = WeatherSourceProvider.OPEN_METEO,
        )
    )

    @Before
    fun setup() {
        prefs = mockk()
        openMeteoAdapter = mockk()
        openMeteoBomAdapter = mockk()
        openMeteoKmaAdapter = mockk()
        openMeteoUkmoAdapter = mockk()
        openMeteoDmiAdapter = mockk()
        openMeteoMeteoFranceAdapter = mockk()
        openMeteoMinutelyAdapter = mockk()
        openMeteoMeteoFranceMinutelyAdapter = mockk()
        alertAdapter = mockk()
        aqiAdapter = mockk()
        owmForecastAdapter = mockk()
        owmAlertAdapter = mockk()
        owmAqiAdapter = mockk()
        pirateWeatherAdapter = mockk()
        brightSkyForecastAdapter = mockk()
        brightSkyAlertAdapter = mockk()
        metNorwayForecastAdapter = mockk()
        ecccForecastAdapter = mockk()
        hkoForecastAdapter = mockk()
        hkoAlertAdapter = mockk()
        bmkgAlertAdapter = mockk()
        geoSphereAustriaAlertAdapter = mockk()
        geoSphereAustriaNowcastAdapter = mockk()
        timeZoneResolver = mockk()
        providerHealthRepository = mockk(relaxed = true)
        every { prefs.settings } returns flowOf(defaultSettings)
        manager = WeatherSourceManager(
            prefs = prefs,
            adapters = weatherSourceAdapters(),
            timeZoneResolver = timeZoneResolver,
            providerHealthRepository = providerHealthRepository,
        )
    }

    private fun weatherSourceAdapters(): Map<WeatherSourceProvider, WeatherSourceAdapter> = mapOf(
        WeatherSourceProvider.OPEN_METEO to WeatherSourceAdapterModule.provideOpenMeteoAdapter(
            openMeteoAdapter,
            aqiAdapter,
            openMeteoMinutelyAdapter,
        ),
        WeatherSourceProvider.OPEN_METEO_BOM to WeatherSourceAdapterModule.provideOpenMeteoBomAdapter(
            openMeteoBomAdapter,
        ),
        WeatherSourceProvider.OPEN_METEO_KMA to WeatherSourceAdapterModule.provideOpenMeteoKmaAdapter(
            openMeteoKmaAdapter,
        ),
        WeatherSourceProvider.OPEN_METEO_UKMO to WeatherSourceAdapterModule.provideOpenMeteoUkmoAdapter(
            openMeteoUkmoAdapter,
        ),
        WeatherSourceProvider.OPEN_METEO_DMI to WeatherSourceAdapterModule.provideOpenMeteoDmiAdapter(
            openMeteoDmiAdapter,
        ),
        WeatherSourceProvider.OPEN_METEO_METEO_FRANCE to WeatherSourceAdapterModule.provideOpenMeteoMeteoFranceAdapter(
            openMeteoMeteoFranceAdapter,
            openMeteoMeteoFranceMinutelyAdapter,
        ),
        WeatherSourceProvider.NWS to WeatherSourceAdapterModule.provideNwsAlertAdapter(alertAdapter),
        WeatherSourceProvider.METEOALARM to WeatherSourceAdapterModule.provideMeteoAlarmAlertAdapter(alertAdapter),
        WeatherSourceProvider.JMA to WeatherSourceAdapterModule.provideJmaAlertAdapter(alertAdapter),
        WeatherSourceProvider.OPEN_WEATHER_MAP to WeatherSourceAdapterModule.provideOwmAdapter(
            owmForecastAdapter,
            owmAlertAdapter,
            owmAqiAdapter,
        ),
        WeatherSourceProvider.PIRATE_WEATHER to WeatherSourceAdapterModule.providePirateWeatherAdapter(
            pirateWeatherAdapter,
        ),
        WeatherSourceProvider.BRIGHT_SKY to WeatherSourceAdapterModule.provideBrightSkyAdapter(
            brightSkyForecastAdapter,
            brightSkyAlertAdapter,
        ),
        WeatherSourceProvider.MET_NORWAY to WeatherSourceAdapterModule.provideMetNorwayAdapter(
            metNorwayForecastAdapter,
        ),
        WeatherSourceProvider.ENVIRONMENT_CANADA to WeatherSourceAdapterModule.provideEnvironmentCanadaAdapter(
            ecccForecastAdapter,
            alertAdapter,
        ),
        WeatherSourceProvider.HKO to WeatherSourceAdapterModule.provideHkoAdapter(
            hkoForecastAdapter,
            hkoAlertAdapter,
        ),
        WeatherSourceProvider.BMKG to WeatherSourceAdapterModule.provideBmkgAdapter(
            bmkgAlertAdapter,
        ),
        WeatherSourceProvider.GEOSPHERE_AUSTRIA to WeatherSourceAdapterModule.provideGeoSphereAustriaAdapter(
            geoSphereAustriaAlertAdapter,
            geoSphereAustriaNowcastAdapter,
        ),
    )

    // ── Forecast Tests ──

    @Test
    fun getWeatherReturnsSuccessFromPrimarySource() = runTest {
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(40.0, -74.0)
        assertTrue(result.isSuccess)
        assertEquals("Test City", result.getOrNull()?.location?.name)
        coVerify(exactly = 1) {
            providerHealthRepository.recordSuccess(
                type = WeatherDataType.FORECAST,
                provider = WeatherSourceProvider.OPEN_METEO,
                cacheAgeMinutes = any(),
                activeFallback = false,
                fallbackFromProvider = null,
                nowEpochMs = any(),
            )
        }
    }

    @Test
    fun getWeatherFallsBackWhenPrimaryFailsWithSameProvider() = runTest {
        val settingsWithFallback = defaultSettings.copy(
            sourceConfig = SourceConfig(
                forecast = WeatherSourceProvider.OPEN_METEO,
                forecastFallback = WeatherSourceProvider.OPEN_METEO,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithFallback)
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.failure(Exception("Primary failed"))

        val result = manager.getWeather(40.0, -74.0)
        // Since fallback == primary, it returns the original failure
        assertTrue(result.isFailure)
        coVerify(exactly = 1) {
            providerHealthRepository.recordFailure(
                type = WeatherDataType.FORECAST,
                provider = WeatherSourceProvider.OPEN_METEO,
                exception = any(),
                clearActiveFallback = true,
                nowEpochMs = any(),
            )
        }
    }

    @Test
    fun getWeatherReturnsFailureWhenNoFallbackConfigured() = runTest {
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.failure(Exception("Network error"))

        val result = manager.getWeather(40.0, -74.0)
        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
        coVerify(exactly = 1) {
            providerHealthRepository.recordFailure(
                type = WeatherDataType.FORECAST,
                provider = WeatherSourceProvider.OPEN_METEO,
                exception = any(),
                clearActiveFallback = true,
                nowEpochMs = any(),
            )
        }
    }

    @Test
    fun getWeatherRecordsFallbackAsActiveWhenPrimaryFailsAndFallbackSucceeds() = runTest {
        val settingsWithFallback = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.OPEN_METEO,
                forecastFallback = WeatherSourceProvider.OPEN_METEO_BOM,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithFallback)
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.failure(Exception("Primary failed"))
        coEvery { openMeteoBomAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(40.0, -74.0)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.usedFallback == true)
        coVerify(exactly = 1) {
            providerHealthRepository.recordFailure(
                type = WeatherDataType.FORECAST,
                provider = WeatherSourceProvider.OPEN_METEO,
                exception = any(),
                clearActiveFallback = false,
                nowEpochMs = any(),
            )
        }
        coVerify(exactly = 1) {
            providerHealthRepository.recordSuccess(
                type = WeatherDataType.FORECAST,
                provider = WeatherSourceProvider.OPEN_METEO_BOM,
                cacheAgeMinutes = any(),
                activeFallback = true,
                fallbackFromProvider = WeatherSourceProvider.OPEN_METEO,
                nowEpochMs = any(),
            )
        }
    }

    @Test
    fun getWeatherDelegatesBomProviderToOpenMeteoBomAdapter() = runTest {
        val settingsWithBom = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.OPEN_METEO_BOM,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithBom)
        coEvery { openMeteoBomAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(-33.86, 151.21, "Sydney")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            openMeteoBomAdapter.getWeather(-33.86, 151.21, "Sydney")
        }
    }

    @Test
    fun getWeatherDelegatesDmiProviderToOpenMeteoDmiAdapter() = runTest {
        val settingsWithDmi = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.OPEN_METEO_DMI,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithDmi)
        coEvery { openMeteoDmiAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(55.68, 12.57, "Copenhagen")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            openMeteoDmiAdapter.getWeather(55.68, 12.57, "Copenhagen")
        }
    }

    @Test
    fun getWeatherDelegatesMeteoFranceProviderToOpenMeteoMeteoFranceAdapter() = runTest {
        val settingsWithMeteoFrance = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.OPEN_METEO_METEO_FRANCE,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithMeteoFrance)
        coEvery { openMeteoMeteoFranceAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(48.8566, 2.3522, "Paris")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            openMeteoMeteoFranceAdapter.getWeather(48.8566, 2.3522, "Paris")
        }
    }

    @Test
    fun getWeatherNormalizesSuspendedKmaProviderToOpenMeteo() = runTest {
        val settingsWithKma = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.OPEN_METEO_KMA,
                forecastFallback = WeatherSourceProvider.OPEN_METEO_KMA,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithKma)
        coEvery { openMeteoAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(37.56, 126.98, "Seoul")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            openMeteoAdapter.getWeather(37.56, 126.98, "Seoul")
        }
        coVerify(exactly = 0) {
            openMeteoKmaAdapter.getWeather(any(), any(), any())
        }
    }

    @Test
    fun getWeatherUsesPerLocationForecastOverrideOverGlobalDefault() = runTest {
        coEvery { openMeteoBomAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(
            latitude = -33.86,
            longitude = 151.21,
            locationName = "Sydney",
            sourceOverrides = SourceOverrides(forecast = WeatherSourceProvider.OPEN_METEO_BOM),
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            openMeteoBomAdapter.getWeather(-33.86, 151.21, "Sydney")
        }
        coVerify(exactly = 0) {
            openMeteoAdapter.getWeather(any(), any(), any())
        }
    }

    @Test
    fun getWeatherPassesResolvedLocationZoneToUtcProjectedForecastProvider() = runTest {
        val settingsWithBrightSky = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.BRIGHT_SKY,
            )
        )
        val berlin = ZoneId.of("Europe/Berlin")
        every { prefs.settings } returns flowOf(settingsWithBrightSky)
        coEvery { timeZoneResolver.resolveZone(52.5, 13.4, "Europe/Berlin") } returns berlin
        coEvery { brightSkyForecastAdapter.getWeather(any(), any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(
            latitude = 52.5,
            longitude = 13.4,
            locationName = "Berlin",
            locationTimeZone = "Europe/Berlin",
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            brightSkyForecastAdapter.getWeather(52.5, 13.4, "Berlin", berlin)
        }
    }

    @Test
    fun getWeatherDelegatesHkoProviderToHkoAdapter() = runTest {
        val settingsWithHko = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                forecast = WeatherSourceProvider.HKO,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithHko)
        coEvery { hkoForecastAdapter.getWeather(any(), any(), any()) } returns Result.success(testWeatherData)

        val result = manager.getWeather(22.3027, 114.1772, "Hong Kong")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            hkoForecastAdapter.getWeather(22.3027, 114.1772, "Hong Kong")
        }
    }

    // ── Alert Tests ──

    @Test
    fun getAlertsReturnsSuccessFromNws() = runTest {
        val alerts = listOf(
            WeatherAlert(
                id = "1", event = "Tornado Warning", headline = "Tornado",
                description = "Take shelter", instruction = null,
                severity = AlertSeverity.EXTREME, urgency = AlertUrgency.IMMEDIATE,
                certainty = "Observed", senderName = "NWS",
                areaDescription = "Test County", effective = null, expires = null, response = null,
            )
        )
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(alerts)

        val result = manager.getAlerts(40.0, -74.0)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        // NWS is the built-in default alert source — it passes no override so
        // AlertRepository honors the Settings alert-source preference.
        coVerify(exactly = 1) {
            alertAdapter.getAlerts(40.0, -74.0, null, includeMeteredSources = true, countryHint = null)
        }
        coVerify(exactly = 1) {
            providerHealthRepository.recordSuccess(
                type = WeatherDataType.ALERTS,
                provider = WeatherSourceProvider.NWS,
                cacheAgeMinutes = null,
                activeFallback = false,
                fallbackFromProvider = null,
                nowEpochMs = any(),
            )
        }
    }

    @Test
    fun getAlertsPassesDedicatedOverrideForExplicitNonDefaultProvider() = runTest {
        val settingsWithMeteoAlarm = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                alerts = WeatherSourceProvider.METEOALARM,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithMeteoAlarm)
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(52.5, 13.4)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            alertAdapter.getAlerts(
                52.5,
                13.4,
                AlertSourcePreference.METEOALARM_ONLY,
                includeMeteredSources = true,
                countryHint = null,
            )
        }
    }

    @Test
    fun getAlertsUsesPerLocationAlertOverrideOverGlobalDefault() = runTest {
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(
            latitude = 52.5,
            longitude = 13.4,
            sourceOverrides = SourceOverrides(alerts = WeatherSourceProvider.METEOALARM),
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            alertAdapter.getAlerts(
                52.5,
                13.4,
                AlertSourcePreference.METEOALARM_ONLY,
                includeMeteredSources = true,
                countryHint = null,
            )
        }
    }

    @Test
    fun getAlertsCanExcludeMeteredAlertSources() = runTest {
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(
            latitude = 40.0,
            longitude = -74.0,
            includeMeteredSources = false,
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            alertAdapter.getAlerts(40.0, -74.0, null, includeMeteredSources = false, countryHint = null)
        }
    }

    @Test
    fun getAlertsReturnsFailureWhenSourceFailsNoFallback() = runTest {
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.failure(Exception("NWS down"))

        val result = manager.getAlerts(40.0, -74.0)
        assertTrue(result.isFailure)
        coVerify(exactly = 1) {
            providerHealthRepository.recordFailure(
                type = WeatherDataType.ALERTS,
                provider = WeatherSourceProvider.NWS,
                exception = any(),
                clearActiveFallback = true,
                nowEpochMs = any(),
            )
        }
    }

    @Test
    fun getAlertsRoutesEnvironmentCanadaThroughInternationalAdapter() = runTest {
        val settingsWithCanada = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                alerts = WeatherSourceProvider.ENVIRONMENT_CANADA,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithCanada)
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(43.7, -79.4)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            alertAdapter.getAlerts(
                43.7,
                -79.4,
                AlertSourcePreference.ECCC_ONLY,
                includeMeteredSources = true,
                countryHint = null,
            )
        }
    }

    @Test
    fun getAlertsForwardsCountryHintToInternationalAdapter() = runTest {
        coEvery { alertAdapter.getAlerts(any(), any(), any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(
            latitude = 51.5,
            longitude = -0.1,
            countryHint = "United Kingdom",
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            alertAdapter.getAlerts(
                51.5,
                -0.1,
                null,
                includeMeteredSources = true,
                countryHint = "United Kingdom",
            )
        }
    }

    @Test
    fun getAlertsDelegatesHkoProviderToHkoAdapter() = runTest {
        val settingsWithHko = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                alerts = WeatherSourceProvider.HKO,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithHko)
        coEvery { hkoAlertAdapter.getAlerts(any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(22.3027, 114.1772)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            hkoAlertAdapter.getAlerts(22.3027, 114.1772)
        }
        coVerify(exactly = 0) {
            alertAdapter.getAlerts(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun getAlertsDelegatesBmkgProviderToBmkgAdapter() = runTest {
        val settingsWithBmkg = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                alerts = WeatherSourceProvider.BMKG,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithBmkg)
        coEvery { bmkgAlertAdapter.getAlerts(any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(-6.2, 106.8)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            bmkgAlertAdapter.getAlerts(-6.2, 106.8)
        }
        coVerify(exactly = 0) {
            alertAdapter.getAlerts(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun getAlertsDelegatesGeoSphereAustriaProviderToGeoSphereAustriaAdapter() = runTest {
        val settingsWithGeoSphere = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                alerts = WeatherSourceProvider.GEOSPHERE_AUSTRIA,
            )
        )
        every { prefs.settings } returns flowOf(settingsWithGeoSphere)
        coEvery { geoSphereAustriaAlertAdapter.getAlerts(any(), any()) } returns Result.success(emptyList())

        val result = manager.getAlerts(48.2082, 16.3738)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            geoSphereAustriaAlertAdapter.getAlerts(48.2082, 16.3738)
        }
        coVerify(exactly = 0) {
            alertAdapter.getAlerts(any(), any(), any(), any(), any())
        }
    }

    // ── Air Quality Tests ──

    @Test
    fun getAirQualityDelegatesToOpenMeteoAdapter() = runTest {
        val aqiData = mockk<AirQualityData>()
        coEvery { aqiAdapter.getAirQuality(any(), any()) } returns Result.success(aqiData)

        val result = manager.getAirQuality(40.0, -74.0)
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { aqiAdapter.getAirQuality(40.0, -74.0) }
    }

    @Test
    fun getAirQualityPropagatesFailure() = runTest {
        coEvery { aqiAdapter.getAirQuality(any(), any()) } returns Result.failure(Exception("AQI error"))

        val result = manager.getAirQuality(40.0, -74.0)
        assertTrue(result.isFailure)
    }

    // ── Minutely Tests ──

    @Test
    fun getMinutelyPrecipitationReturnsDataFromOpenMeteo() = runTest {
        val minutely = listOf(
            MinutelyPrecipitation(time = LocalDateTime.now(), precipitation = 0.5)
        )
        coEvery { openMeteoMinutelyAdapter.getMinutelyPrecipitation(any(), any()) } returns Result.success(minutely)

        val result = manager.getMinutelyPrecipitation(40.0, -74.0)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun getMinutelyPrecipitationPropagatesFailure() = runTest {
        coEvery { openMeteoMinutelyAdapter.getMinutelyPrecipitation(any(), any()) } returns Result.failure(Exception("Minutely error"))

        val result = manager.getMinutelyPrecipitation(40.0, -74.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun getMinutelyPrecipitationDelegatesMeteoFranceProviderToMeteoFranceAdapter() = runTest {
        val settingsWithMeteoFrance = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                minutely = WeatherSourceProvider.OPEN_METEO_METEO_FRANCE,
            )
        )
        val minutely = listOf(
            MinutelyPrecipitation(time = LocalDateTime.now(), precipitation = 0.3)
        )
        every { prefs.settings } returns flowOf(settingsWithMeteoFrance)
        coEvery { openMeteoMeteoFranceMinutelyAdapter.getMinutelyPrecipitation(any(), any()) } returns Result.success(minutely)

        val result = manager.getMinutelyPrecipitation(48.8566, 2.3522)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        coVerify(exactly = 1) {
            openMeteoMeteoFranceMinutelyAdapter.getMinutelyPrecipitation(48.8566, 2.3522)
        }
        coVerify(exactly = 0) {
            openMeteoMinutelyAdapter.getMinutelyPrecipitation(any(), any())
        }
    }

    @Test
    fun getMinutelyPrecipitationDelegatesGeoSphereAustriaProviderToGeoSphereAustriaAdapter() = runTest {
        val settingsWithGeoSphere = defaultSettings.copy(
            sourceConfig = defaultSettings.sourceConfig.copy(
                minutely = WeatherSourceProvider.GEOSPHERE_AUSTRIA,
            )
        )
        val minutely = listOf(
            MinutelyPrecipitation(time = LocalDateTime.now(), precipitation = 0.2)
        )
        every { prefs.settings } returns flowOf(settingsWithGeoSphere)
        coEvery { geoSphereAustriaNowcastAdapter.getMinutelyPrecipitation(any(), any()) } returns Result.success(minutely)

        val result = manager.getMinutelyPrecipitation(48.2082, 16.3738)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        coVerify(exactly = 1) {
            geoSphereAustriaNowcastAdapter.getMinutelyPrecipitation(48.2082, 16.3738)
        }
        coVerify(exactly = 0) {
            openMeteoMinutelyAdapter.getMinutelyPrecipitation(any(), any())
        }
    }

    // ── Source Config Tests ──

    @Test
    fun registryCoversEverySelectableProviderType() {
        val registry = weatherSourceAdapters()

        WeatherDataType.entries.forEach { type ->
            WeatherSourceProvider.forType(type).forEach { provider ->
                val adapter = registry[provider]
                assertNotNull("${provider.name} is missing a WeatherSourceAdapter binding", adapter)
                assertTrue(
                    "${provider.name} adapter does not advertise ${type.name}",
                    type in requireNotNull(adapter).supportedTypes,
                )
            }
        }
    }

    @Test
    fun forTypeReturnsOnlyImplementedForecastProviders() {
        val forecastProviders = WeatherSourceProvider.forType(WeatherDataType.FORECAST)
        assertTrue("OWM should be present", forecastProviders.contains(WeatherSourceProvider.OPEN_WEATHER_MAP))
        assertTrue("Pirate Weather should be present", forecastProviders.contains(WeatherSourceProvider.PIRATE_WEATHER))
        assertTrue("Bright Sky should be present", forecastProviders.contains(WeatherSourceProvider.BRIGHT_SKY))
        assertTrue("Open-Meteo should be present", forecastProviders.contains(WeatherSourceProvider.OPEN_METEO))
        assertTrue(
            "Open-Meteo BOM ACCESS-G should be present",
            forecastProviders.contains(WeatherSourceProvider.OPEN_METEO_BOM),
        )
        assertTrue(
            "Open-Meteo KMA should stay hidden while upstream updates are suspended",
            !forecastProviders.contains(WeatherSourceProvider.OPEN_METEO_KMA),
        )
        assertTrue(
            "Open-Meteo UKMO should be present",
            forecastProviders.contains(WeatherSourceProvider.OPEN_METEO_UKMO),
        )
        assertTrue(
            "Open-Meteo DMI should be present",
            forecastProviders.contains(WeatherSourceProvider.OPEN_METEO_DMI),
        )
        assertTrue(
            "Open-Meteo Meteo-France should be present",
            forecastProviders.contains(WeatherSourceProvider.OPEN_METEO_METEO_FRANCE),
        )
        assertTrue(
            "MET Norway should be present once implemented",
            forecastProviders.contains(WeatherSourceProvider.MET_NORWAY),
        )
        assertTrue(
            "Environment Canada forecast should be present once implemented",
            forecastProviders.contains(WeatherSourceProvider.ENVIRONMENT_CANADA),
        )
        assertTrue(
            "HKO forecast should be present once implemented",
            forecastProviders.contains(WeatherSourceProvider.HKO),
        )
    }

    @Test
    fun fromStoredNameRejectsSuspendedKmaForecastProvider() {
        assertNull(
            WeatherSourceProvider.fromStoredName(
                WeatherSourceProvider.OPEN_METEO_KMA.name,
                WeatherDataType.FORECAST,
            ),
        )
    }

    @Test
    fun sourceConfigNormalizesSuspendedKmaSelections() {
        val normalized = SourceConfig(
            forecast = WeatherSourceProvider.OPEN_METEO_KMA,
            forecastFallback = WeatherSourceProvider.OPEN_METEO_KMA,
        ).normalized()

        assertEquals(WeatherSourceProvider.OPEN_METEO, normalized.forecast)
        assertNull(normalized.forecastFallback)
    }

    @Test
    fun forTypeReturnsImplementedAlertProviders() {
        val alertProviders = WeatherSourceProvider.forType(WeatherDataType.ALERTS)
        assertTrue(alertProviders.contains(WeatherSourceProvider.NWS))
        assertTrue(alertProviders.contains(WeatherSourceProvider.ENVIRONMENT_CANADA))
        assertTrue(alertProviders.contains(WeatherSourceProvider.HKO))
        assertTrue(alertProviders.contains(WeatherSourceProvider.BMKG))
        assertTrue(alertProviders.contains(WeatherSourceProvider.GEOSPHERE_AUSTRIA))
        assertTrue(alertProviders.contains(WeatherSourceProvider.METEOALARM))
        assertTrue(alertProviders.contains(WeatherSourceProvider.JMA))
    }

    @Test
    fun forTypeReturnsImplementedMinutelyProviders() {
        val minutelyProviders = WeatherSourceProvider.forType(WeatherDataType.MINUTELY)

        assertTrue(minutelyProviders.contains(WeatherSourceProvider.OPEN_METEO))
        assertTrue(minutelyProviders.contains(WeatherSourceProvider.OPEN_METEO_METEO_FRANCE))
        assertTrue(minutelyProviders.contains(WeatherSourceProvider.GEOSPHERE_AUSTRIA))
    }
}
