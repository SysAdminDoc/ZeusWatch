package com.sysadmindoc.nimbus.wear.tile

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.sysadmindoc.nimbus.wear.data.WearLocationProvider
import com.sysadmindoc.nimbus.wear.data.WearWeatherData
import com.sysadmindoc.nimbus.wear.data.WearWeatherRepository
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

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
        val service = WeatherTileService()
        service.repository = repository
        service.locationProvider = locationProvider
        service.syncedStore = syncedStore

        val data = service.loadTileData()

        assertEquals(72, data!!.temperature)
        assertEquals("Seattle", data.locationName)
        coVerify(exactly = 0) { locationProvider.getLocation() }
        coVerify(exactly = 0) { repository.getCurrentWeather(any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `tile request runner completes tile future on happy path`() = runTest {
        val future = WeatherTileRequestRunner.requestTile(
            scope = this,
            loadData = {
                WearWeatherData(
                    temperature = 72,
                    condition = "Clear Sky",
                    high = 80,
                    low = 62,
                    locationName = "Seattle",
                )
            },
            buildTile = { data ->
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(data?.condition ?: "fallback")
                    .setFreshnessIntervalMillis(30 * 60 * 1000L)
                    .build()
            },
        )

        advanceUntilIdle()
        val tile = future.get(5, TimeUnit.SECONDS)

        assertEquals("Clear Sky", tile.resourcesVersion)
        assertEquals(30 * 60 * 1000L, tile.freshnessIntervalMillis)
    }

    @Test
    fun `onTileResourcesRequest completes resource future`() {
        val service = Robolectric.buildService(WeatherTileService::class.java).get()

        val resources = service.invokeResourcesRequest()
            .get(5, TimeUnit.SECONDS)

        assertEquals("2", resources.version)
    }

    @Suppress("UNCHECKED_CAST")
    private fun WeatherTileService.invokeResourcesRequest(): Future<androidx.wear.protolayout.ResourceBuilders.Resources> {
        val method = WeatherTileService::class.java.getDeclaredMethod(
            "onTileResourcesRequest",
            RequestBuilders.ResourcesRequest::class.java,
        )
        method.isAccessible = true
        return method.invoke(this, RequestBuilders.ResourcesRequest.Builder().build())
            as Future<androidx.wear.protolayout.ResourceBuilders.Resources>
    }
}
