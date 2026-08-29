package com.sysadmindoc.nimbus.wear.tile

import androidx.wear.tiles.Material3TileService
import com.sysadmindoc.nimbus.wear.R
import com.sysadmindoc.nimbus.wear.data.DataSource
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.data.WearWeatherResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `loadTileData passes the location resolver through to the repository`() = runTest {
        val expected = WearWeatherResult.Available(
            WearWeatherData(
                temperature = 72,
                condition = "Clear Sky",
                high = 80,
                low = 62,
                locationName = "Seattle",
                dataSource = DataSource.PHONE_SYNC,
                syncedAtMs = 1_720_000_000_000L,
            ),
        )
        val resolvers = mutableListOf<suspend () -> WearLocationProvider.LocationResult?>()
        val repository = mockk<WearWeatherRepository>()
        coEvery { repository.loadWeather(capture(resolvers)) } returns expected
        val locationProvider = mockk<WearLocationProvider>()
        coEvery { locationProvider.getLocation() } returns
            WearLocationProvider.LocationResult(47.61, -122.33, "Seattle")
        val service = Robolectric.buildService(WeatherTileService::class.java).get()
        service.repository = repository
        service.locationProvider = locationProvider

        val result = service.loadTileData()

        assertEquals(expected, result)
        // The repository decides whether a location fix is worth its battery
        // cost; the tile must not resolve one up front.
        coVerify(exactly = 0) { locationProvider.getLocation() }
        assertEquals(
            WearLocationProvider.LocationResult(47.61, -122.33, "Seattle"),
            resolvers.single().invoke(),
        )
        coVerify(exactly = 1) { locationProvider.getLocation() }
    }

    @Test
    fun `tile data load degrades to a failure result on non-cancellation errors`() = runTest {
        val repository = mockk<WearWeatherRepository>()
        coEvery { repository.loadWeather(any()) } throws IllegalStateException("store unavailable")
        val service = Robolectric.buildService(WeatherTileService::class.java).get()
        service.repository = repository
        service.locationProvider = mockk(relaxed = true)

        val result = service.loadTileDataForTile()

        assertTrue(result is WearWeatherResult.Failed)
        assertEquals("store unavailable", (result as WearWeatherResult.Failed).error.message)
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
