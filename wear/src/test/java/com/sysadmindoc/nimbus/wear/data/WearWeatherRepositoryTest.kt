package com.sysadmindoc.nimbus.wear.data

import android.content.Context
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherStore
import com.sysadmindoc.nimbus.wear.sync.SyncedWeatherPayload
import com.sysadmindoc.nimbus.wear.testing.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WearWeatherRepository].
 *
 * The direct API path is locked down with an OkHttp interceptor so the watch
 * fallback can be tested without network access. The companion helpers are
 * used by the tile, complication, hourly screen, and synced-store fallback;
 * drift there silently mislabels weather on the watch.
 */
class WearWeatherRepositoryTest {

    @Test
    fun `getCurrentWeather maps direct Open-Meteo response`() = runTest {
        val requests = mutableListOf<Request>()
        val repository = WearWeatherRepository(
            client = okHttpClient(
                code = 200,
                body = """
                    {
                      "current": {
                        "temperature_2m": 68.4,
                        "weather_code": 61,
                        "relative_humidity_2m": 83,
                        "wind_speed_10m": 9.8,
                        "uv_index": 5.7,
                        "is_day": 0
                      },
                      "daily": {
                        "temperature_2m_max": [75.2],
                        "temperature_2m_min": [52.9],
                        "precipitation_probability_max": [60]
                      },
                      "hourly": {
                        "time": ["2026-05-17T10:00", "2026-05-17T11:00"],
                        "temperature_2m": [67.8, 69.1],
                        "weather_code": [61, 63],
                        "precipitation_probability": [60, 70],
                        "wind_speed_10m": [8.0, 10.5]
                      }
                    }
                """.trimIndent(),
                onRequest = requests::add,
            ),
            syncedStore = emptySyncedStore(),
        )

        val result = repository.getCurrentWeather(47.61, -122.33, "Seattle")

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(68, data.temperature)
        assertEquals("Rain", data.condition)
        assertEquals(75, data.high)
        assertEquals(52, data.low)
        assertEquals("Seattle", data.locationName)
        assertEquals(83, data.humidity)
        assertEquals(9, data.windSpeed)
        assertEquals(5, data.uvIndex)
        assertEquals(60, data.precipChance)
        assertFalse(data.isDay)
        assertEquals(61, data.weatherCode)
        assertEquals(DataSource.DIRECT_API, data.dataSource)
        assertTrue(data.syncedAtMs > 0L)
        assertEquals(2, data.hourly.size)
        assertEquals("2026-05-17T11:00", data.hourly[1].time)
        assertEquals(69, data.hourly[1].temperature)
        assertEquals(63, data.hourly[1].weatherCode)
        assertEquals(70, data.hourly[1].precipChance)
        assertEquals(10, data.hourly[1].windSpeed)

        val request = requests.single()
        assertEquals("api.open-meteo.com", request.url.host)
        assertEquals("47.61", request.url.queryParameter("latitude"))
        assertEquals("-122.33", request.url.queryParameter("longitude"))
        assertEquals("12", request.url.queryParameter("forecast_hours"))
        assertTrue(request.header("User-Agent")!!.startsWith("ZeusWatch-Wear/"))
    }

    @Test
    fun `getCurrentWeather prefers fresh synced data over network`() = runTest {
        val timestamp = System.currentTimeMillis()
        val store = emptySyncedStore()
        store.save(
            SyncedWeatherPayload(
                temperature = 72,
                condition = "Clear Sky",
                high = 80,
                low = 62,
                locationName = "Phone",
                humidity = 45,
                windSpeed = 4,
                uvIndex = 6,
                precipChance = 5,
                isDay = true,
                weatherCode = 0,
                timestampMs = timestamp,
                hourly = listOf(HourlyEntry("2026-05-17T10:00", 72, 0)),
            ),
        )
        val repository = WearWeatherRepository(
            client = okHttpClient(onRequest = { error("network should not be used for fresh sync") }),
            syncedStore = store,
        )

        val data = repository.getCurrentWeather(0.0, 0.0, "Ignored").getOrThrow()

        assertEquals(72, data.temperature)
        assertEquals("Phone", data.locationName)
        assertEquals(DataSource.PHONE_SYNC, data.dataSource)
        assertEquals(timestamp, data.syncedAtMs)
        assertEquals(1, data.hourly.size)
    }

    @Test
    fun `getCurrentWeather direct path keeps the last synced display units`() = runTest {
        val store = emptySyncedStore()
        // A stale payload (>30 min) forces the direct API path, but the
        // last-synced display units must still drive rendering.
        store.save(
            SyncedWeatherPayload(
                temperature = 72,
                condition = "Clear Sky",
                high = 80,
                low = 62,
                locationName = "Phone",
                humidity = 45,
                windSpeed = 4,
                uvIndex = 6,
                precipChance = 5,
                isDay = true,
                weatherCode = 0,
                timestampMs = System.currentTimeMillis() - 31 * 60 * 1000L,
                hourly = emptyList(),
                tempUnit = "FAHRENHEIT",
                windUnit = "MPH",
            ),
        )
        val repository = WearWeatherRepository(
            client = okHttpClient(
                code = 200,
                body = """{"current": {"temperature_2m": 20.0, "weather_code": 0, "is_day": 1}}""",
            ),
            syncedStore = store,
        )

        val data = repository.getCurrentWeather(47.61, -122.33, "Seattle").getOrThrow()

        assertEquals(DataSource.DIRECT_API, data.dataSource)
        assertEquals("FAHRENHEIT", data.tempUnit)
        assertEquals("MPH", data.windUnit)
    }

    @Test
    fun `getCurrentWeather reports non-successful API responses`() = runTest {
        val repository = WearWeatherRepository(
            client = okHttpClient(code = 503, body = """{"error": true}"""),
            syncedStore = emptySyncedStore(),
        )

        val result = repository.getCurrentWeather(47.61, -122.33, "Seattle")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("503"))
    }

    @Test
    fun `wmoDescription maps clear sky and overcast bands`() {
        assertEquals("Clear Sky", WearWeatherRepository.wmoDescription(0))
        assertEquals("Mostly Clear", WearWeatherRepository.wmoDescription(1))
        assertEquals("Partly Cloudy", WearWeatherRepository.wmoDescription(2))
        assertEquals("Overcast", WearWeatherRepository.wmoDescription(3))
    }

    @Test
    fun `wmoDescription maps fog and freezing precipitation`() {
        assertEquals("Fog", WearWeatherRepository.wmoDescription(45))
        assertEquals("Fog", WearWeatherRepository.wmoDescription(48))
        assertEquals("Drizzle", WearWeatherRepository.wmoDescription(53))
        assertEquals("Freezing Drizzle", WearWeatherRepository.wmoDescription(56))
        assertEquals("Freezing Rain", WearWeatherRepository.wmoDescription(67))
    }

    @Test
    fun `wmoDescription maps rain, showers, and snow ranges`() {
        assertEquals("Rain", WearWeatherRepository.wmoDescription(61))
        assertEquals("Rain", WearWeatherRepository.wmoDescription(65))
        assertEquals("Snow", WearWeatherRepository.wmoDescription(71))
        assertEquals("Snow", WearWeatherRepository.wmoDescription(75))
        assertEquals("Snow Grains", WearWeatherRepository.wmoDescription(77))
        assertEquals("Showers", WearWeatherRepository.wmoDescription(80))
        assertEquals("Showers", WearWeatherRepository.wmoDescription(82))
        assertEquals("Snow Showers", WearWeatherRepository.wmoDescription(85))
    }

    @Test
    fun `wmoDescription maps thunderstorm and hail variants`() {
        assertEquals("Thunderstorm", WearWeatherRepository.wmoDescription(95))
        assertEquals("Thunderstorm + Hail", WearWeatherRepository.wmoDescription(96))
        assertEquals("Thunderstorm + Hail", WearWeatherRepository.wmoDescription(99))
    }

    @Test
    fun `wmoDescription falls back to Unknown for unsupported codes`() {
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(-1))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(4))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(50))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(100))
        assertEquals("Unknown", WearWeatherRepository.wmoDescription(Int.MAX_VALUE))
    }

    @Test
    fun `wmoEmoji distinguishes day and night for clear and mostly clear`() {
        // U+2600 sun for day clear, U+1F319 crescent moon for night clear.
        assertEquals("☀️", WearWeatherRepository.wmoEmoji(0, isDay = true))
        assertEquals("🌙", WearWeatherRepository.wmoEmoji(0, isDay = false))
        // U+1F324 sun behind small cloud for day mostly clear.
        assertEquals("🌤️", WearWeatherRepository.wmoEmoji(1, isDay = true))
        assertEquals("🌙", WearWeatherRepository.wmoEmoji(1, isDay = false))
    }

    @Test
    fun `wmoEmoji uses isDay-agnostic glyphs for overcast and precipitation`() {
        val cloudy = WearWeatherRepository.wmoEmoji(2, isDay = true)
        assertEquals(cloudy, WearWeatherRepository.wmoEmoji(2, isDay = false))
        val overcast = WearWeatherRepository.wmoEmoji(3, isDay = true)
        assertEquals(overcast, WearWeatherRepository.wmoEmoji(3, isDay = false))
        val rain = WearWeatherRepository.wmoEmoji(63, isDay = true)
        assertEquals(rain, WearWeatherRepository.wmoEmoji(63, isDay = false))
        val snow = WearWeatherRepository.wmoEmoji(73, isDay = true)
        assertEquals(snow, WearWeatherRepository.wmoEmoji(73, isDay = false))
    }

    @Test
    fun `wmoEmoji maps thunderstorm range to thunder cloud glyph`() {
        // U+26C8 thunder cloud and rain.
        val expected = "⛈️"
        assertEquals(expected, WearWeatherRepository.wmoEmoji(95))
        assertEquals(expected, WearWeatherRepository.wmoEmoji(96))
        assertEquals(expected, WearWeatherRepository.wmoEmoji(99))
    }

    @Test
    fun `wmoEmoji fog range uses fog glyph regardless of day or night`() {
        val expected = "🌫️"
        assertEquals(expected, WearWeatherRepository.wmoEmoji(45, isDay = true))
        assertEquals(expected, WearWeatherRepository.wmoEmoji(48, isDay = false))
    }

    @Test
    fun `wmoEmoji falls back to thermometer for unknown codes`() {
        // U+1F321 thermometer for unknowns so the user still sees a glyph.
        val fallback = "🌡️"
        assertEquals(fallback, WearWeatherRepository.wmoEmoji(-1))
        assertEquals(fallback, WearWeatherRepository.wmoEmoji(100))
        assertEquals(fallback, WearWeatherRepository.wmoEmoji(Int.MAX_VALUE))
    }

    private fun emptySyncedStore(): SyncedWeatherStore {
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns FakeSharedPreferences()
        return SyncedWeatherStore(context)
    }

    private fun okHttpClient(
        code: Int = 200,
        body: String = "{}",
        onRequest: (Request) -> Unit = {},
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                onRequest(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            },
        )
        .build()
}
