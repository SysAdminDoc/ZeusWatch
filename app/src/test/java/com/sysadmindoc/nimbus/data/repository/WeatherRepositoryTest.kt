package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.GeocodingApi
import com.sysadmindoc.nimbus.data.api.GeocodingResponse
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.api.OpenMeteoApi
import com.sysadmindoc.nimbus.data.api.WeatherDao
import com.sysadmindoc.nimbus.data.location.ReverseGeocoder
import com.sysadmindoc.nimbus.data.model.CurrentWeather
import com.sysadmindoc.nimbus.data.model.DailyWeather
import com.sysadmindoc.nimbus.data.model.HourlyWeather
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
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

    @Before
    fun setup() {
        weatherApi = mockk()
        geocodingApi = mockk()
        reverseGeocoder = mockk()
        weatherDao = mockk(relaxUnitFun = true)
        userPreferences = mockk()
        sourceManager = mockk()

        every { userPreferences.settings } returns flowOf(NimbusSettings())

        repository = WeatherRepository(
            weatherApi = weatherApi,
            geocodingApi = geocodingApi,
            reverseGeocoder = reverseGeocoder,
            weatherDao = weatherDao,
            userPreferences = userPreferences,
            sourceManager = dagger.Lazy { sourceManager },
        )
    }

    // --- getWeather delegates to sourceManager ---

    @Test
    fun getWeatherDelegatesToSourceManager() = runTest {
        val fakeData = mockk<WeatherData>()
        coEvery { sourceManager.getWeather(testLat, testLon, testLocationName) } returns Result.success(fakeData)

        val result = repository.getWeather(testLat, testLon, testLocationName)

        assertTrue(result.isSuccess)
        assertEquals(fakeData, result.getOrThrow())
        coVerify(exactly = 1) { sourceManager.getWeather(testLat, testLon, testLocationName) }
    }

    @Test
    fun getWeatherReturnsFailureFromSourceManager() = runTest {
        val error = RuntimeException("API down")
        coEvery { sourceManager.getWeather(testLat, testLon, null) } returns Result.failure(error)

        val result = repository.getWeather(testLat, testLon)

        assertTrue(result.isFailure)
        assertEquals("API down", result.exceptionOrNull()?.message)
    }

    // --- getWeatherDirect ---

    @Test
    fun getWeatherDirectReturnsApiDataAndCachesIt() = runTest {
        val response = makeResponse(temp = 25.0, weatherCode = 2)
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns response
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
}
