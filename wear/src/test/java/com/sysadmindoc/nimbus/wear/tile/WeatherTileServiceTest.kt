package com.sysadmindoc.nimbus.wear.tile

import androidx.wear.tiles.Material3TileService
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherTileServiceTest {

    @Test
    fun `loadTileData returns fresh synced data without repository fallback`() = runTest {
        val syncedStore = mockk<SyncedWeatherStore>()
        every { syncedStore.getFreshData() } returns WearWeatherData(
            temperature = 72,
            condition = "Clear Sky",
            high = 80,
            low = 62,
            locationName = "Seattle",
            humidity = 45,
            windSpeed = 4,
            uvIndex = 6,
            precipChance = 5,
            isDay = true,
            weatherCode = 0,
        )
        val repository = mockk<WearWeatherRepository>(relaxed = true)
        val locationProvider = mockk<WearLocationProvider>(relaxed = true)
        val service = Robolectric.buildService(WeatherTileService::class.java).get()
        service.repository = repository
        service.locationProvider = locationProvider
        service.syncedStore = syncedStore

        val data = service.loadTileData()

        assertEquals(72, data!!.temperature)
        assertEquals("Seattle", data.locationName)
        coVerify(exactly = 0) { locationProvider.getLocation() }
        coVerify(exactly = 0) { repository.getCurrentWeather(any(), any(), any()) }
    }

    @Test
    fun `tile data load degrades to no data on non-cancellation failure`() = runTest {
        val syncedStore = mockk<SyncedWeatherStore>()
        every { syncedStore.getFreshData() } throws IllegalStateException("store unavailable")
        val service = Robolectric.buildService(WeatherTileService::class.java).get()
        service.repository = mockk(relaxed = true)
        service.locationProvider = mockk(relaxed = true)
        service.syncedStore = syncedStore

        assertNull(service.loadTileDataForTile())
    }

    @Test
    fun `service uses Material3TileService base`() {
        assertTrue(Material3TileService::class.java.isAssignableFrom(WeatherTileService::class.java))
    }

    @Test
    fun `weather lottie resource points at raw tile animation`() {
        val imageResource = WeatherTileLottieResources.weatherIcon()
        val lottie = imageResource.androidLottieResourceByResId

        assertNotNull(lottie)
        assertEquals(R.raw.weather_tile_clear, lottie!!.rawResourceId)
        assertNotNull(lottie.startTrigger)
    }

    @Test
    fun `tile keeps thirty minute freshness interval`() {
        assertEquals(30 * 60 * 1000L, WEATHER_TILE_FRESHNESS_MS)
    }
}
