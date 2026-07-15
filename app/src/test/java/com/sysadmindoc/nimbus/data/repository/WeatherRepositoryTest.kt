package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.GeocodingResponse
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.WeatherDao
import com.sysadmindoc.nimbus.data.location.ReverseGeocoder
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.CurrentWeather
import com.sysadmindoc.nimbus.data.model.DailyWeather
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.HourlyWeather
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCachePayload
import com.sysadmindoc.nimbus.data.model.toCachePayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class WeatherRepositoryTest {

    private lateinit var weatherApi: OpenMeteoApi
    private lateinit var geocodingApi: GeocodingApi
    private lateinit var reverseGeocoder: ReverseGeocoder
    private lateinit var weatherDao: WeatherDao
    private lateinit var userPreferences: UserPreferences
    private lateinit var sourceManager: WeatherSourceManager
    private lateinit var repository: WeatherRepository

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val testLat = 39.74
    private val testLon = -104.98
    private val testLocationName = "Denver"

    private fun makeResponse(
        temp: Double = 22.0,
        weatherCode: Int = 0,
        hourlyTimes: List<String> = emptyList(),
    ) = OpenMeteoResponse(
        latitude = testLat,
        longitude = testLon,
        current = CurrentWeather(
            time = "2025-06-15T12:00",
            temperature = temp,
            apparentTemperature = temp - 1.0,
            humidity = 45,
            weatherCode = weatherCode,
            isDay = 1,
            windSpeed = 10.0,
            windDirection = 180,
            pressureMsl = 1013.0,
            uvIndex = 5.0,
            precipitation = 0.0,
            cloudCover = 20,
        ),
        hourly = if (hourlyTimes.isNotEmpty()) HourlyWeather(
            time = hourlyTimes,
            temperature = hourlyTimes.map { 20.0 },
        ) else null,
        daily = DailyWeather(
            time = listOf("2025-06-15"),
            temperatureMax = listOf(28.0),
            temperatureMin = listOf(15.0),
        ),
    )

    private fun makeHourlyOnlyBomResponse() = OpenMeteoResponse(
        latitude = -33.86,
        longitude = 151.21,
        timezone = "Australia/Sydney",
        current = null,
        hourly = HourlyWeather(
            time = listOf("2025-06-15T12:00"),
            temperature = listOf(18.5),
            apparentTemperature = listOf(17.0),
            humidity = listOf(72),
            weatherCode = listOf(61),
            isDay = listOf(1),
            windSpeed = listOf(22.0),
            windDirection = listOf(210),
            precipitation = listOf(0.8),
            cloudCover = listOf(84),
            surfacePressure = listOf(1009.0),
            visibility = listOf(12000.0),
        ),
        daily = DailyWeather(
            time = listOf("2025-06-15"),
            temperatureMax = listOf(21.0),
            temperatureMin = listOf(12.0),
            precipitationSum = listOf(4.5),
        ),
    )

    private fun makeCacheEntity(
        responseJson: String = json.encodeToString(OpenMeteoResponse.serializer(), makeResponse()),
        cachedAt: Long = System.currentTimeMillis(),
    ) = WeatherCacheEntity(
        locationKey = WeatherCacheEntity.makeKey(testLat, testLon),
        responseJson = responseJson,
        locationName = testLocationName,
        locationRegion = "Colorado",
        locationCountry = "US",
        latitude = testLat,
        longitude = testLon,
        cachedAt = cachedAt,
    )

    private fun makeWeatherData(
        sourceProvider: String? = WeatherSourceProvider.OPEN_METEO.displayName,
        usedFallback: Boolean = false,
        temperature: Double = 22.0,
        lastUpdated: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
    ) = WeatherData(
        location = LocationInfo(
            name = testLocationName,
            region = "Colorado",
            country = "US",
            latitude = testLat,
            longitude = testLon,
            timeZone = "America/Denver",
        ),
        current = CurrentConditions(
            temperature = temperature,
            feelsLike = temperature - 1.0,
            humidity = 45,
            weatherCode = WeatherCode.CLEAR_SKY,
            observationTime = lastUpdated,
            isDay = true,
            windSpeed = 10.0,
            windDirection = 180,
            windGusts = null,
            pressure = 1013.0,
            uvIndex = 5.0,
            visibility = 10000.0,
            dewPoint = 6.0,
            cloudCover = 20,
            precipitation = 0.0,
            dailyHigh = 28.0,
            dailyLow = 15.0,
            sunrise = "05:30",
            sunset = "20:30",
        ),
        hourly = emptyList(),
        daily = emptyList(),
        lastUpdated = lastUpdated,
        sourceProvider = sourceProvider,
        usedFallback = usedFallback,
    )

    private fun makeWeatherDataCacheEntity(
        data: WeatherData,
        provider: WeatherSourceProvider,
        savedLocationId: Long? = null,
        cachedAt: Long = System.currentTimeMillis(),
    ) = WeatherDataCacheEntity(
        cacheKey = WeatherDataCacheEntity.makeKey(
            latitude = testLat,
            longitude = testLon,
            sourceProvider = provider.name,
            savedLocationId = savedLocationId,
        ),
        locationKey = WeatherCacheEntity.makeKey(testLat, testLon),
        sourceProvider = provider.name,
        savedLocationId = savedLocationId,
        schemaVersion = WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
        payloadJson = json.encodeToString(
            WeatherDataCachePayload.serializer(),
            data.toCachePayload(),
        ),
        cachedAt = cachedAt,
    )

    private fun routeWeatherData() = WeatherData(
        location = LocationInfo(
            name = "Route point",
            region = "Colorado",
            country = "US",
            latitude = testLat,
            longitude = testLon,
        ),
        current = CurrentConditions(
            temperature = 1.0,
            feelsLike = -2.0,
            humidity = 88,
            weatherCode = WeatherCode.FREEZING_RAIN_LIGHT,
            observationTime = LocalDateTime.of(2026, 1, 1, 8, 0),
            isDay = true,
            windSpeed = 54.0,
            windDirection = 270,
            windGusts = 72.0,
            pressure = 1008.0,
            uvIndex = 0.0,
            visibility = 900.0,
            dewPoint = -1.0,
            cloudCover = 100,
            precipitation = 1.2,
            dailyHigh = 2.0,
            dailyLow = -3.0,
            sunrise = "07:15",
            sunset = "16:45",
        ),
        hourly = listOf(
            HourlyConditions(
                time = LocalDateTime.of(2026, 1, 1, 8, 0),
                temperature = 1.0,
                feelsLike = -2.0,
                weatherCode = WeatherCode.FREEZING_RAIN_LIGHT,
                isDay = true,
                precipitationProbability = 80,
                precipitation = 1.2,
                windSpeed = 54.0,
                windDirection = 270,
                humidity = 88,
                uvIndex = 0.0,
                cloudCover = 100,
                visibility = 900.0,
                windGusts = 72.0,
            )
        ),
        daily = emptyList(),
        lastUpdated = LocalDateTime.of(2026, 1, 1, 8, 0),
    )

    @Before
    fun setup() {
        weatherApi = mockk()
        geocodingApi = mockk()
        reverseGeocoder = mockk()
        weatherDao = mockk(relaxUnitFun = true)
        userPreferences = mockk()
        sourceManager = mockk()

        every { userPreferences.settings } returns flowOf(NimbusSettings())
        coEvery { weatherDao.getCachedWeatherData(any(), any(), any(), any()) } returns null
        coEvery { weatherDao.getCached(any()) } returns null

        repository = WeatherRepository(
            weatherApi = weatherApi,
            geocodingApi = geocodingApi,
            reverseGeocoder = reverseGeocoder,
            weatherDao = weatherDao,
            userPreferences = userPreferences,
            openMeteoFlatBufferAdapter = OpenMeteoFlatBufferAdapter(),
            sourceManager = dagger.Lazy { sourceManager },
        )
    }

    // --- getWeather delegates to sourceManager ---

    @Test
    fun getWeatherDelegatesToSourceManager() = runTest {
        val fakeData = mockk<WeatherData>()
        coEvery {
            sourceManager.getWeather(testLat, testLon, testLocationName, null, SourceOverrides())
        } returns Result.success(fakeData)

        val result = repository.getWeather(testLat, testLon, testLocationName)

        assertTrue(result.isSuccess)
        assertEquals(fakeData, result.getOrThrow())
        coVerify(exactly = 1) {
            sourceManager.getWeather(testLat, testLon, testLocationName, null, SourceOverrides())
        }
    }

    @Test
    fun getWeatherReturnsFailureFromSourceManager() = runTest {
        val error = RuntimeException("API down")
        coEvery { sourceManager.getWeather(testLat, testLon, null, null, SourceOverrides()) } returns Result.failure(error)

        val result = repository.getWeather(testLat, testLon)

        assertTrue(result.isFailure)
        assertEquals("API down", result.exceptionOrNull()?.message)
    }

    // --- getWeatherDirect ---

    @Test
    fun getWeatherDirectReturnsApiDataAndCachesIt() = runTest {
        val response = makeResponse(temp = 25.0, weatherCode = 2)
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response
        coEvery {
            weatherApi.getForecastFlatBuffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("FlatBuffers should be disabled")
        coEvery { reverseGeocoder.resolve(testLat, testLon) } returns com.sysadmindoc.nimbus.data.location.ReverseGeoResult(
            name = testLocationName, region = "Colorado", country = "US",
        )

        val result = repository.getWeatherDirect(testLat, testLon)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(25.0, data.current.temperature, 0.01)
        assertEquals(testLocationName, data.location.name)

        // Verify cache was written
        coVerify(exactly = 1) { weatherDao.upsert(any()) }
        coVerify(exactly = 0) {
            weatherApi.getForecastFlatBuffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun getWeatherDirectFallsBackToJsonWhenFlatBufferPathFails() = runTest {
        every { userPreferences.settings } returns flowOf(NimbusSettings(openMeteoFlatBuffersEnabled = true))
        val response = makeResponse(temp = 26.0, weatherCode = 3)
        coEvery {
            weatherApi.getForecastFlatBuffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("bad flatbuffer")
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response
        coEvery { reverseGeocoder.resolve(testLat, testLon) } returns null

        val result = repository.getWeatherDirect(testLat, testLon, testLocationName)

        assertTrue(result.isSuccess)
        assertEquals(26.0, result.getOrThrow().current.temperature, 0.01)
        coVerify(exactly = 1) {
            weatherApi.getForecastFlatBuffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) {
            weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun getWeatherDirectUsesProvidedLocationName() = runTest {
        val response = makeResponse()
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.getWeatherDirect(testLat, testLon, "Custom City")

        assertTrue(result.isSuccess)
        assertEquals("Custom City", result.getOrThrow().location.name)
    }

    @Test
    fun getWeatherDirectConvertsSnowDepthMetersToCentimeters() = runTest {
        // Open-Meteo snow_depth arrives in meters; the formatter contract is cm.
        val base = makeResponse()
        val response = base.copy(current = base.current!!.copy(snowDepth = 0.12))
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.getWeatherDirect(testLat, testLon, testLocationName)

        assertTrue(result.isSuccess)
        assertEquals(12.0, result.getOrThrow().current.snowDepth!!, 0.001)
    }

    @Test
    fun getWeatherDirectLeavesNullSnowDepthNull() = runTest {
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makeResponse()

        val result = repository.getWeatherDirect(testLat, testLon, testLocationName)

        assertNull(result.getOrThrow().current.snowDepth)
    }

    @Test
    fun getWeatherDirectReturnsFailureOnApiException() = runTest {
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = repository.getWeatherDirect(testLat, testLon, testLocationName)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun getWeatherDirectCacheFailureIsNonFatal() = runTest {
        val response = makeResponse()
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response
        coEvery { reverseGeocoder.resolve(testLat, testLon) } returns com.sysadmindoc.nimbus.data.location.ReverseGeoResult(
            name = testLocationName, region = "", country = "",
        )
        coEvery { weatherDao.upsert(any()) } throws RuntimeException("DB write failed")

        val result = repository.getWeatherDirect(testLat, testLon)

        // Should still succeed despite cache failure
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrThrow())
    }

    @Test
    fun getWeatherDirectUsesConfiguredCacheTtlWhenPruningCache() = runTest {
        every { userPreferences.settings } returns flowOf(NimbusSettings(cacheTtlMinutes = 15))
        repository = WeatherRepository(
            weatherApi = weatherApi,
            geocodingApi = geocodingApi,
            reverseGeocoder = reverseGeocoder,
            weatherDao = weatherDao,
            userPreferences = userPreferences,
            openMeteoFlatBufferAdapter = OpenMeteoFlatBufferAdapter(),
            sourceManager = dagger.Lazy { sourceManager },
        )

        val response = makeResponse()
        val cutoffSlot = slot<Long>()
        val before = System.currentTimeMillis()
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response
        coEvery { reverseGeocoder.resolve(testLat, testLon) } returns com.sysadmindoc.nimbus.data.location.ReverseGeoResult(
            name = testLocationName, region = "Colorado", country = "US",
        )
        coEvery { weatherDao.deleteOlderThan(capture(cutoffSlot)) } returns Unit

        repository.getWeatherDirect(testLat, testLon)

        val after = System.currentTimeMillis()
        val expectedTtl = 15 * 60 * 1000L
        assertTrue(cutoffSlot.captured in (before - expectedTtl)..(after - expectedTtl))
    }

    @Test
    fun getWeatherDirectFallsBackToOpenMeteoReverseGeocode() = runTest {
        val response = makeResponse()
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response
        coEvery { reverseGeocoder.resolve(testLat, testLon) } returns null
        coEvery {
            geocodingApi.reverseGeocode(testLat, testLon, any(), any(), any())
        } returns GeocodingResponse(
            results = listOf(
                GeocodingResult(
                    id = 99,
                    name = "Aurora",
                    latitude = testLat,
                    longitude = testLon,
                    country = "United States",
                    admin1 = "Colorado",
                )
            )
        )

        val result = repository.getWeatherDirect(testLat, testLon)

        assertTrue(result.isSuccess)
        assertEquals("Aurora", result.getOrThrow().location.name)
        assertEquals("Colorado", result.getOrThrow().location.region)
    }

    @Test
    fun getBomWeatherDirectMapsHourlyOnlyResponseToCurrentConditions() = runTest {
        coEvery {
            weatherApi.getBomForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns makeHourlyOnlyBomResponse()

        val result = repository.getBomWeatherDirect(-33.86, 151.21, "Sydney")

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Sydney", data.location.name)
        assertEquals(18.5, data.current.temperature, 0.01)
        assertEquals(17.0, data.current.feelsLike, 0.01)
        assertEquals(72, data.current.humidity)
        assertEquals(61, data.current.weatherCode.code)
        assertEquals(22.0, data.current.windSpeed, 0.01)
        assertEquals(21.0, data.current.dailyHigh, 0.01)
        assertEquals(12.0, data.current.dailyLow, 0.01)
    }

    @Test
    fun getDmiWeatherDirectUsesDmiSeamlessModelAndMapsHourlyOnlyResponse() = runTest {
        coEvery {
            weatherApi.getDmiForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns makeHourlyOnlyBomResponse()

        val result = repository.getDmiWeatherDirect(55.68, 12.57, "Copenhagen")

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("Copenhagen", data.location.name)
        assertEquals(18.5, data.current.temperature, 0.01)
        coVerify(exactly = 1) {
            weatherApi.getDmiForecast(
                55.68,
                12.57,
                "dmi_seamless",
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    // --- getCachedWeather ---

    @Test
    fun getCachedWeatherReturnsCachedData() = runTest {
        val cached = makeCacheEntity()
        coEvery { weatherDao.getCached(any()) } returns cached

        val data = repository.getCachedWeather(testLat, testLon)

        assertNotNull(data)
        assertEquals(testLocationName, data!!.location.name)
        assertEquals(22.0, data.current.temperature, 0.01)
    }

    @Test
    fun getCachedWeatherReturnsNullWhenNoCacheExists() = runTest {
        coEvery { weatherDao.getCached(any()) } returns null

        val data = repository.getCachedWeather(testLat, testLon)

        assertNull(data)
    }

    @Test
    fun getCachedWeatherReturnsNullOnCorruptJson() = runTest {
        val cached = makeCacheEntity(responseJson = "{ not valid json !!!")
        coEvery { weatherDao.getCached(any()) } returns cached

        val data = repository.getCachedWeather(testLat, testLon)

        assertNull(data)
    }

    @Test
    fun getCachedWeatherHonorsConfiguredCacheTtl() = runTest {
        every { userPreferences.settings } returns flowOf(NimbusSettings(cacheTtlMinutes = 15))
        repository = WeatherRepository(
            weatherApi = weatherApi,
            geocodingApi = geocodingApi,
            reverseGeocoder = reverseGeocoder,
            weatherDao = weatherDao,
            userPreferences = userPreferences,
            openMeteoFlatBufferAdapter = OpenMeteoFlatBufferAdapter(),
            sourceManager = dagger.Lazy { sourceManager },
        )
        val cached = makeCacheEntity(cachedAt = System.currentTimeMillis() - (16 * 60 * 1000L))
        coEvery { weatherDao.getCached(any()) } returns cached

        val data = repository.getCachedWeather(testLat, testLon)

        assertNull(data)
    }

    @Test
    fun getCachedWeatherUsesCorrectLocationKey() = runTest {
        val keySlot = slot<String>()
        coEvery { weatherDao.getCached(capture(keySlot)) } returns null

        repository.getCachedWeather(testLat, testLon)

        assertEquals(WeatherCacheEntity.makeKey(testLat, testLon), keySlot.captured)
    }

    @Test
    fun getCachedWeatherPreservesLocationFields() = runTest {
        val cached = makeCacheEntity()
        coEvery { weatherDao.getCached(any()) } returns cached

        val data = repository.getCachedWeather(testLat, testLon)

        assertNotNull(data)
        assertEquals("Colorado", data!!.location.region)
        assertEquals("US", data.location.country)
        assertEquals(testLat, data.location.latitude, 0.01)
        assertEquals(testLon, data.location.longitude, 0.01)
    }

    @Test
    fun getCachedWeatherReturnsProviderAwareNormalizedCache() = runTest {
        val cachedAt = System.currentTimeMillis() - (5 * 60 * 1000L)
        val provider = WeatherSourceProvider.MET_NORWAY
        val entity = makeWeatherDataCacheEntity(
            data = makeWeatherData(
                sourceProvider = provider.displayName,
                temperature = 14.5,
            ),
            provider = provider,
            savedLocationId = 42L,
            cachedAt = cachedAt,
        )
        coEvery {
            weatherDao.getCachedWeatherData(
                WeatherCacheEntity.makeKey(testLat, testLon),
                42L,
                listOf(provider.name),
                WeatherDataCacheEntity.CURRENT_SCHEMA_VERSION,
            )
        } returns entity

        val data = repository.getCachedWeather(
            latitude = testLat,
            longitude = testLon,
            sourceOverrides = SourceOverrides(forecast = provider),
            savedLocationId = 42L,
        )

        assertNotNull(data)
        assertEquals(provider.displayName, data!!.sourceProvider)
        assertEquals(14.5, data.current.temperature, 0.01)
        val expected = java.time.Instant.ofEpochMilli(cachedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        assertEquals(expected, data.lastUpdated)
    }

    @Test
    fun getCachedWeatherDoesNotUseLegacyOpenMeteoCacheForDifferentProvider() = runTest {
        val provider = WeatherSourceProvider.MET_NORWAY
        coEvery { weatherDao.getCached(any()) } returns makeCacheEntity()

        val data = repository.getCachedWeather(
            latitude = testLat,
            longitude = testLon,
            sourceOverrides = SourceOverrides(forecast = provider),
        )

        assertNull(data)
        coVerify(exactly = 0) { weatherDao.getCached(any()) }
    }

    @Test
    fun getWeatherWritesNormalizedCacheForActualFallbackProvider() = runTest {
        val fallbackProvider = WeatherSourceProvider.MET_NORWAY
        every { userPreferences.settings } returns flowOf(
            NimbusSettings(
                sourceConfig = SourceConfig(
                    forecast = WeatherSourceProvider.OPEN_METEO,
                    forecastFallback = fallbackProvider,
                )
            )
        )
        val weather = makeWeatherData(
            sourceProvider = fallbackProvider.displayName,
            usedFallback = true,
        )
        val entitySlot = slot<WeatherDataCacheEntity>()
        coEvery {
            sourceManager.getWeather(testLat, testLon, null, null, SourceOverrides())
        } returns Result.success(weather)
        coEvery { weatherDao.upsertWeatherData(capture(entitySlot)) } returns Unit

        val result = repository.getWeather(
            latitude = testLat,
            longitude = testLon,
            savedLocationId = 42L,
        )

        assertTrue(result.isSuccess)
        val entity = entitySlot.captured
        assertEquals(fallbackProvider.name, entity.sourceProvider)
        assertEquals(42L, entity.savedLocationId)
        assertEquals(WeatherCacheEntity.makeKey(testLat, testLon), entity.locationKey)
        val payload = json.decodeFromString(WeatherDataCachePayload.serializer(), entity.payloadJson)
        assertTrue(payload.usedFallback)
        assertEquals(fallbackProvider.displayName, payload.sourceProvider)
    }

    // --- planDrivingRouteWeather ---

    @Test
    fun planDrivingRouteWeatherSamplesForecastsAndEvaluatesWaypointRisk() = runTest {
        val departure = LocalDateTime.of(2026, 1, 1, 8, 0)
        coEvery {
            geocodingApi.searchLocation("Denver", any(), any(), any())
        } returns GeocodingResponse(
            results = listOf(
                GeocodingResult(
                    id = 1,
                    name = "Denver",
                    latitude = 39.7392,
                    longitude = -104.9903,
                    country = "United States",
                    admin1 = "Colorado",
                    timezone = "America/Denver",
                )
            )
        )
        coEvery {
            geocodingApi.searchLocation("Boulder", any(), any(), any())
        } returns GeocodingResponse(
            results = listOf(
                GeocodingResult(
                    id = 2,
                    name = "Boulder",
                    latitude = 40.0150,
                    longitude = -105.2705,
                    country = "United States",
                    admin1 = "Colorado",
                    timezone = "America/Denver",
                )
            )
        )
        coEvery {
            sourceManager.getWeather(any(), any(), any(), any(), any())
        } returns Result.success(routeWeatherData())
        coEvery {
            sourceManager.getAlertsDetailed(
                latitude = any(),
                longitude = any(),
                sourceOverrides = any(),
                includeMeteredSources = false,
                countryHint = any(),
            )
        } returns AlertFetchResult(emptyList(), allAdaptersFailed = false, failedSources = emptyList())

        val result = repository.planDrivingRouteWeather(
            originQuery = "Denver",
            destinationQuery = "Boulder",
            departureTime = departure,
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertEquals("Denver", plan.origin.name)
        assertEquals("Boulder", plan.destination.name)
        assertTrue(plan.waypoints.size >= 2)
        assertEquals(departure, plan.waypoints.first().arrivalTime)
        assertTrue(plan.waypoints.any { it.conditions?.iceRisk == true })
        assertTrue(plan.waypoints.any { it.risk == DrivingRouteRiskLevel.HIGH })
        assertEquals(DrivingRouteEstimateKind.STRAIGHT_LINE_CORRIDOR, plan.estimateKind)
        assertEquals(DEFAULT_DRIVING_ROUTE_SPEED_KMH, plan.assumedSpeedKmh, 0.0)
    }

    @Test
    fun planDrivingRouteWeatherUsesGpxGeometryAndPreservesFailedSamples() = runTest {
        val departure = LocalDateTime.of(2026, 1, 1, 8, 0)
        val geometry = DrivingRouteGeometry(
            points = listOf(
                DrivingRoutePoint(0.0, 0.0),
                DrivingRoutePoint(0.0, 1.0),
                DrivingRoutePoint(1.0, 1.0),
            ),
            estimateKind = DrivingRouteEstimateKind.GPX_ROUTE,
        )
        coEvery {
            sourceManager.getWeather(0.0, 0.0, any(), null, SourceOverrides())
        } returns Result.success(routeWeatherData())
        coEvery {
            sourceManager.getWeather(0.0, 1.0, any(), null, SourceOverrides())
        } returns Result.failure(IllegalStateException("sample unavailable"))
        coEvery {
            sourceManager.getWeather(1.0, 1.0, any(), null, SourceOverrides())
        } returns Result.success(routeWeatherData())
        coEvery {
            sourceManager.getAlertsDetailed(
                latitude = any(),
                longitude = any(),
                sourceOverrides = any(),
                includeMeteredSources = false,
                countryHint = any(),
            )
        } returns AlertFetchResult(emptyList(), allAdaptersFailed = false, failedSources = emptyList())

        val result = repository.planDrivingRouteWeather(
            originQuery = "",
            destinationQuery = "",
            departureTime = departure,
            routeGeometry = geometry,
            averageSpeedKmh = 111.2,
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertEquals(DrivingRouteEstimateKind.GPX_ROUTE, plan.estimateKind)
        assertTrue(plan.distanceKm > 220.0)
        assertEquals(3, plan.waypoints.size)
        assertEquals(1, plan.unavailableWaypointCount)
        assertEquals(null, plan.waypoints[1].conditions)
        assertEquals(1.0, plan.waypoints[1].longitude, 0.01)
    }

    @Test
    fun planDrivingRouteWeatherRetriesSharedCityStateRouteLabels() = runTest {
        val departure = LocalDateTime.of(2026, 1, 1, 8, 0)
        val fallbackOrigin = LocationInfo(
            name = "Map center",
            region = "Colorado",
            country = "United States",
            latitude = 39.7392,
            longitude = -104.9903,
        )
        coEvery {
            geocodingApi.searchLocation("Boulder, CO", any(), any(), any())
        } returns GeocodingResponse(results = emptyList())
        coEvery {
            geocodingApi.searchLocation("Boulder Colorado", any(), any(), any())
        } returns GeocodingResponse(
            results = listOf(
                GeocodingResult(
                    id = 2,
                    name = "Boulder",
                    latitude = 40.0150,
                    longitude = -105.2705,
                    country = "United States",
                    admin1 = "Colorado",
                    timezone = "America/Denver",
                )
            )
        )
        coEvery {
            sourceManager.getWeather(any(), any(), any(), any(), any())
        } returns Result.success(routeWeatherData())
        coEvery {
            sourceManager.getAlertsDetailed(
                latitude = any(),
                longitude = any(),
                sourceOverrides = any(),
                includeMeteredSources = false,
                countryHint = any(),
            )
        } returns AlertFetchResult(emptyList(), allAdaptersFailed = false, failedSources = emptyList())

        val result = repository.planDrivingRouteWeather(
            originQuery = "",
            destinationQuery = "Boulder, CO",
            departureTime = departure,
            fallbackOrigin = fallbackOrigin,
        )

        assertTrue(result.isSuccess)
        assertEquals("Boulder", result.getOrThrow().destination.name)
        coVerify(exactly = 1) {
            geocodingApi.searchLocation("Boulder, CO", any(), any(), any())
        }
        coVerify(exactly = 1) {
            geocodingApi.searchLocation("Boulder Colorado", any(), any(), any())
        }
    }

    // --- getWeatherOrCached (background-worker stale fallback) ---

    @Test
    fun getWeatherOrCachedReturnsLiveDataWhenFetchSucceeds() = runTest {
        val fakeData = mockk<WeatherData>()
        coEvery {
            sourceManager.getWeather(testLat, testLon, null, null, SourceOverrides())
        } returns Result.success(fakeData)

        val result = repository.getWeatherOrCached(testLat, testLon)

        assertTrue(result.isSuccess)
        assertEquals(fakeData, result.getOrThrow())
        // Live success must not touch the cache for a fallback read.
        coVerify(exactly = 0) { weatherDao.getCached(any()) }
    }

    @Test
    fun getWeatherOrCachedFallsBackToFreshCacheWhenFetchFails() = runTest {
        coEvery {
            sourceManager.getWeather(testLat, testLon, null, null, SourceOverrides())
        } returns Result.failure(RuntimeException("offline"))
        val cachedAt = System.currentTimeMillis() - (90 * 60 * 1000L) // 90 min old
        coEvery { weatherDao.getCached(any()) } returns makeCacheEntity(cachedAt = cachedAt)

        val result = repository.getWeatherOrCached(testLat, testLon)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(testLocationName, data.location.name)
        // Stale data is flagged via lastUpdated reflecting the real cache age.
        val expected = java.time.Instant.ofEpochMilli(cachedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        assertEquals(expected, data.lastUpdated)
    }

    @Test
    fun getWeatherOrCachedReturnsFailureWhenFetchFailsAndNoCache() = runTest {
        coEvery {
            sourceManager.getWeather(testLat, testLon, null, null, SourceOverrides())
        } returns Result.failure(RuntimeException("offline"))
        coEvery { weatherDao.getCached(any()) } returns null

        val result = repository.getWeatherOrCached(testLat, testLon)

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }

    @Test
    fun getWeatherOrCachedReturnsFailureWhenCacheOlderThanStaleWindow() = runTest {
        coEvery {
            sourceManager.getWeather(testLat, testLon, null, null, SourceOverrides())
        } returns Result.failure(RuntimeException("offline"))
        // Older than the 6h stale fallback window.
        val cachedAt = System.currentTimeMillis() - (7 * 60 * 60 * 1000L)
        coEvery { weatherDao.getCached(any()) } returns makeCacheEntity(cachedAt = cachedAt)

        val result = repository.getWeatherOrCached(testLat, testLon)

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }

    @Test
    fun getCachedWeatherStampsLastUpdatedFromCacheTime() = runTest {
        // A cache written in the past must report that age via lastUpdated, not
        // "now" — otherwise the "updated X ago" label and staleness colouring
        // always render cached data as fresh.
        val cachedAt = System.currentTimeMillis() - (5 * 60 * 1000L)
        val cached = makeCacheEntity(cachedAt = cachedAt)
        coEvery { weatherDao.getCached(any()) } returns cached

        val data = repository.getCachedWeather(testLat, testLon)

        assertNotNull(data)
        val expected = java.time.Instant.ofEpochMilli(cachedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        assertEquals(expected, data!!.lastUpdated)
    }
}
