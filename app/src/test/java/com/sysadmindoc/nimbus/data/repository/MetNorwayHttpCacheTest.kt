package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.MetNorwayApi
import com.sysadmindoc.nimbus.data.model.MetEntryData
import com.sysadmindoc.nimbus.data.model.MetInstant
import com.sysadmindoc.nimbus.data.model.MetInstantDetails
import com.sysadmindoc.nimbus.data.model.MetNorwayResponse
import com.sysadmindoc.nimbus.data.model.MetProperties
import com.sysadmindoc.nimbus.data.model.MetTimeseriesEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.headersOf
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory

/**
 * MET Norway's terms (2026-11-05) set a minimum cache time on top of the
 * `Expires` header: 30 minutes in the Nordic area, 3 hours everywhere else.
 */
class MetNorwayHttpCacheTest {

    private val dir: File = createTempDirectory("met-cache").toFile()
    private val t0: Instant = Instant.parse("2026-11-15T06:39:37Z")
    private var now: Instant = t0

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun cache() = MetNorwayHttpCache(dir, clock = { now })

    private fun response(temp: Double) = MetNorwayResponse(
        type = "Feature",
        properties = MetProperties(
            timeseries = listOf(
                MetTimeseriesEntry(
                    time = OffsetDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0).toString(),
                    data = MetEntryData(instant = MetInstant(details = MetInstantDetails(airTemperature = temp))),
                ),
            ),
        ),
    )

    // Live New York request on 2026-11-15: Date 06:39:37 GMT, Expires 06:44:02 GMT.
    private fun entryExpiringIn4Minutes() = MetNorwayHttpCache.CacheEntry(
        lastModified = "Sun, 15 Nov 2026 06:30:00 GMT",
        expires = t0.plusSeconds(265),
        response = response(12.0),
        fetchedAt = t0,
    )

    @Test
    fun `a Nordic point stays fresh for 30 minutes past a short Expires`() {
        val cache = cache()
        val entry = entryExpiringIn4Minutes()
        now = t0.plus(Duration.ofMinutes(29))
        assertTrue(cache.isFresh(entry, OSLO_LAT, OSLO_LON))
        now = t0.plus(Duration.ofMinutes(31))
        assertFalse(cache.isFresh(entry, OSLO_LAT, OSLO_LON))
    }

    @Test
    fun `a point outside the Nordic area stays fresh for 3 hours`() {
        val cache = cache()
        val entry = entryExpiringIn4Minutes()
        now = t0.plus(Duration.ofHours(2).plusMinutes(59))
        assertTrue(cache.isFresh(entry, NY_LAT, NY_LON))
        now = t0.plus(Duration.ofHours(3).plusMinutes(1))
        assertFalse(cache.isFresh(entry, NY_LAT, NY_LON))
    }

    @Test
    fun `an Expires later than the floor still wins`() {
        val cache = cache()
        val entry = entryExpiringIn4Minutes().copy(expires = t0.plus(Duration.ofHours(5)))
        now = t0.plus(Duration.ofHours(4))
        assertTrue(cache.isFresh(entry, NY_LAT, NY_LON))
        now = t0.plus(Duration.ofHours(5).plusSeconds(1))
        assertFalse(cache.isFresh(entry, NY_LAT, NY_LON))
    }

    @Test
    fun `a missing Expires falls back to the floor alone`() {
        val cache = cache()
        val entry = entryExpiringIn4Minutes().copy(expires = null)
        now = t0.plus(Duration.ofMinutes(29))
        assertTrue(cache.isFresh(entry, OSLO_LAT, OSLO_LON))
        now = t0.plus(Duration.ofMinutes(31))
        assertFalse(cache.isFresh(entry, OSLO_LAT, OSLO_LON))
    }

    @Test
    fun `the Nordic box covers the Nordic and Baltic capitals and nothing far outside`() {
        listOf(
            59.91 to 10.75, // Oslo
            59.33 to 18.07, // Stockholm
            60.17 to 24.94, // Helsinki
            55.68 to 12.57, // Copenhagen
            59.44 to 24.75, // Tallinn
            69.65 to 18.96, // Tromso
        ).forEach { (lat, lon) ->
            assertTrue("$lat,$lon should be Nordic", MetNorwayHttpCache.isMetNordicArea(lat, lon))
        }
        listOf(
            NY_LAT to NY_LON,
            51.51 to -0.13, // London
            52.52 to 13.40, // Berlin
            78.22 to 15.65, // Longyearbyen, Svalbard
            64.15 to -21.94, // Reykjavik
        ).forEach { (lat, lon) ->
            assertFalse("$lat,$lon should not be Nordic", MetNorwayHttpCache.isMetNordicArea(lat, lon))
        }
    }

    @Test
    fun `the Nordic boxes stop at the borders around the Baltic`() {
        listOf(
            56.95 to 24.10, // Riga
            58.38 to 26.72, // Tartu
            55.71 to 21.13, // Klaipeda
            70.37 to 31.10, // Vardo
            66.50 to 25.73, // Rovaniemi
            62.90 to 27.68, // Kuopio
            60.39 to 5.32, // Bergen
            54.91 to 9.79, // Sonderborg
            54.77 to 11.87, // Nykobing Falster
            54.69 to 11.39, // Rodby, Lolland
            54.89 to 10.41, // Aeroskobing
            55.10 to 14.70, // Ronne, Bornholm
            55.47 to 8.45, // Esbjerg
            55.04 to 9.42, // Aabenraa
            55.38 to 13.16, // Trelleborg
            60.46 to 26.23, // Loviisa
            59.35 to 26.36, // Rakvere
            70.07 to 29.75, // Vadso
            54.86 to 10.52, // Marstal, Aero
            54.92 to 9.60, // Grasten
            55.10 to 8.57, // Havneby, Romo
            55.15 to 8.50, // Lakolk, western Romo
            54.87 to 9.96, // southern Als
        ).forEach { (lat, lon) ->
            assertTrue("$lat,$lon should be Nordic", MetNorwayHttpCache.isMetNordicArea(lat, lon))
        }
        listOf(
            54.71 to 20.51, // Kaliningrad
            59.94 to 30.31, // St Petersburg
            55.19 to 30.20, // Vitebsk
            54.78 to 9.44, // Flensburg
            60.71 to 28.75, // Vyborg
            57.81 to 27.61, // Pechory
            54.35 to 18.65, // Gdansk
            54.68 to 13.43, // Kap Arkona
            55.02 to 8.43, // List, Sylt
            54.86 to 9.58, // Holnis
            54.8718 to 9.6044, // Holnis tip
            54.878 to 9.61, // water off Holnis
            54.65 to 10.20, // Kiel Bight off the Schlei
            54.61 to 12.65, // water off the Darss
            54.53 to 11.20, // Fehmarn
            55.33 to 20.70, // water off the Kaliningrad end of the Curonian Spit
            70.05 to 31.45, // water off Rybachy
            59.69 to 27.15, // Gulf of Finland off Russia's islands
            55.6 to 19.5, // open Baltic
            55.7 to 18.0, // open Baltic
            55.4 to 5.7, // German North Sea waters
            // Each of these sat inside a box until the boxes were checked
            // against the EEZ polygons; all are foreign water.
            60.00 to 26.40, // Russian water off Rodsher
            59.97 to 26.45, // Russian water off Rodsher
            70.005 to 31.19, // Russian water south of Vardo
            54.76 to 10.15, // German water south of Aero
            54.63 to 11.05, // German water off western Lolland
            54.97 to 8.52, // German Wadden Sea north of Sylt
            55.05 to 8.45, // German water at Sylt's north tip, below western Romo
        ).forEach { (lat, lon) ->
            assertFalse("$lat,$lon should not be Nordic", MetNorwayHttpCache.isMetNordicArea(lat, lon))
        }
    }

    @Test
    fun `Expires is measured against the server's Date, not the phone clock`() {
        // Phone clock two hours behind the server.
        val expires = MetNorwayHttpCache.expiresAt(
            "Sun, 15 Nov 2026 08:44:02 GMT", "Sun, 15 Nov 2026 08:39:37 GMT", fetchedAt = t0,
        )
        assertEquals(t0.plusSeconds(265), expires)
        assertEquals(
            Instant.parse("2026-11-15T08:44:02Z"),
            MetNorwayHttpCache.expiresAt("Sun, 15 Nov 2026 08:44:02 GMT", null, fetchedAt = t0),
        )
        assertNull(MetNorwayHttpCache.expiresAt(null, "Sun, 15 Nov 2026 08:39:37 GMT", fetchedAt = t0))
    }

    @Test
    fun `a phone clock behind the server doesn't stretch the Nordic floor`() = runTest {
        val api = mockk<MetNorwayApi>()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(
            response(4.0),
            headersOf("Date", "Sun, 15 Nov 2026 08:39:37 GMT", "Expires", "Sun, 15 Nov 2026 08:44:02 GMT"),
        )
        val adapter = MetNorwayForecastAdapter(api, cache())
        adapter.getWeather(OSLO_LAT, OSLO_LON, "Oslo").getOrThrow()

        now = t0.plus(Duration.ofMinutes(31))
        adapter.getWeather(OSLO_LAT, OSLO_LON, "Oslo").getOrThrow()
        coVerify(exactly = 2) { api.getForecast(any(), any(), any(), any()) }
    }

    @Test
    fun `loads of one coordinate at the same time make one request`() = runTest {
        val api = mockk<MetNorwayApi>()
        coEvery { api.getForecast(any(), any(), any(), any()) } coAnswers {
            delay(1_000)
            Response.success(response(9.0))
        }
        val adapter = MetNorwayForecastAdapter(api, cache())

        val loads = (1..3).map { async { adapter.getWeather(NY_LAT, NY_LON, "New York") } }.awaitAll()

        loads.forEach { assertEquals(9.0, it.getOrThrow().current.temperature, 0.01) }
        coVerify(exactly = 1) { api.getForecast(any(), any(), any(), any()) }
    }

    @Test
    fun `a full cache drops the entry that goes stale first`() = runTest {
        val newYorkArea = (0 until 50).map { i -> 40.0 + i / 100.0 to NY_LON }
        // Fresh for two more hours, against an Oslo entry with ten minutes left.
        val older = entryExpiringIn4Minutes().copy(expires = null, fetchedAt = t0.minus(Duration.ofHours(1)))
        val nearlyStale = entryExpiringIn4Minutes().copy(expires = null, fetchedAt = t0.minus(Duration.ofMinutes(20)))

        val inMemory = MetNorwayHttpCache(null, clock = { now })
        val onDisk = cache()
        listOf(inMemory, onDisk).forEach { cache ->
            newYorkArea.forEach { (lat, lon) -> cache.put(lat, lon, older) }
            cache.put(OSLO_LAT, OSLO_LON, nearlyStale)
        }

        assertNull(inMemory.get(OSLO_LAT, OSLO_LON))
        newYorkArea.forEach { (lat, lon) -> assertNotNull("$lat,$lon", inMemory.get(lat, lon)) }
        val restarted = cache()
        assertNull(restarted.get(OSLO_LAT, OSLO_LON))
        newYorkArea.forEach { (lat, lon) -> assertNotNull("$lat,$lon on disk", restarted.get(lat, lon)) }
    }

    @Test
    fun `an entry stamped in the future is stale`() {
        val cache = cache()
        val entry = entryExpiringIn4Minutes().copy(fetchedAt = t0.plus(Duration.ofHours(1)))
        assertFalse(cache.isFresh(entry, OSLO_LAT, OSLO_LON))
        assertFalse(cache.isFresh(entry, NY_LAT, NY_LON))
    }

    @Test
    fun `a forecast served from the cache reports when it was fetched`() = runTest {
        val api = mockk<MetNorwayApi>()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(response(9.0))
        val adapter = MetNorwayForecastAdapter(api, cache())
        val fetched = LocalDateTime.ofInstant(t0, ZoneId.systemDefault())

        assertEquals(fetched, adapter.getWeather(NY_LAT, NY_LON, "New York").getOrThrow().lastUpdated)

        now = t0.plus(Duration.ofHours(2))
        assertEquals(fetched, adapter.getWeather(NY_LAT, NY_LON, "New York").getOrThrow().lastUpdated)
        coVerify(exactly = 1) { api.getForecast(any(), any(), any(), any()) }
    }

    @Test
    fun `a body the app can't map is not cached`() = runTest {
        val api = mockk<MetNorwayApi>()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(
            MetNorwayResponse(type = "Feature", properties = MetProperties(timeseries = emptyList())),
        )
        val cache = cache()
        val adapter = MetNorwayForecastAdapter(api, cache)

        assertTrue(adapter.getWeather(NY_LAT, NY_LON, "New York").isFailure)
        assertNull(cache.get(NY_LAT, NY_LON))
        assertNull(cache().get(NY_LAT, NY_LON))

        assertTrue(adapter.getWeather(NY_LAT, NY_LON, "New York").isFailure)
        coVerify(exactly = 2) { api.getForecast(any(), any(), any(), any()) }
    }

    @Test
    fun `an Oslo forecast is reused at 29 minutes and asked for again at 31`() = runTest {
        val api = mockk<MetNorwayApi>()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(
            response(4.0),
            headersOf("Last-Modified", "Sun, 15 Nov 2026 06:30:00 GMT", "Expires", "Sun, 15 Nov 2026 06:44:02 GMT"),
        )
        val adapter = MetNorwayForecastAdapter(api, cache())
        adapter.getWeather(OSLO_LAT, OSLO_LON, "Oslo").getOrThrow()

        now = t0.plus(Duration.ofMinutes(29))
        adapter.getWeather(OSLO_LAT, OSLO_LON, "Oslo").getOrThrow()
        coVerify(exactly = 1) { api.getForecast(any(), any(), any(), any()) }

        now = t0.plus(Duration.ofMinutes(31))
        adapter.getWeather(OSLO_LAT, OSLO_LON, "Oslo").getOrThrow()
        coVerify(exactly = 2) { api.getForecast(any(), any(), any(), any()) }
        coVerify(exactly = 1) { api.getForecast(OSLO_LAT, OSLO_LON, any(), "Sun, 15 Nov 2026 06:30:00 GMT") }
    }

    @Test
    fun `concurrent writes of one coordinate leave a readable file`() = runTest {
        val cache = cache()
        withContext(Dispatchers.Default) {
            (1..64).map { i ->
                launch { cache.put(OSLO_LAT, OSLO_LON, entryExpiringIn4Minutes().copy(response = response(i.toDouble()))) }
            }.joinAll()
        }

        val temp = cache().get(OSLO_LAT, OSLO_LON)
            ?.response?.properties?.timeseries?.single()?.data?.instant?.details?.airTemperature
        assertTrue("restored $temp", temp != null && temp in 1.0..64.0)
        assertEquals(emptyList<String>(), dir.list()!!.filter { it.endsWith(".tmp") })
    }

    @Test
    fun `entries survive a new cache instance on the same directory`() = runTest {
        // Built once: the response's timestamp is the current hour, so two
        // builds either side of an hour boundary would differ.
        val entry = entryExpiringIn4Minutes()
        cache().put(OSLO_LAT, OSLO_LON, entry)

        val restored = cache().get(OSLO_LAT, OSLO_LON)

        assertNotNull(restored)
        assertEquals(entry, restored)
    }

    @Test
    fun `an unreadable file is dropped instead of failing the load`() = runTest {
        cache().put(OSLO_LAT, OSLO_LON, entryExpiringIn4Minutes())
        val file = dir.listFiles()!!.single { it.name.endsWith(".json") }
        file.writeText("{not json")

        assertNull(cache().get(OSLO_LAT, OSLO_LON))
        assertFalse(file.exists())
    }

    @Test
    fun `a second load inside the floor makes no request even after process death`() = runTest {
        val api = mockk<MetNorwayApi>()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns Response.success(
            response(9.0),
            headersOf(
                "Last-Modified", "Sun, 15 Nov 2026 06:30:00 GMT",
                "Expires", "Sun, 15 Nov 2026 06:44:02 GMT",
            ),
        )
        MetNorwayForecastAdapter(api, cache()).getWeather(NY_LAT, NY_LON, "New York").getOrThrow()

        // A new process: new cache and adapter, same directory, 2 h 59 m later.
        now = t0.plus(Duration.ofHours(2).plusMinutes(59))
        val data = MetNorwayForecastAdapter(api, cache()).getWeather(NY_LAT, NY_LON, "New York").getOrThrow()

        assertEquals(9.0, data.current.temperature, 0.01)
        coVerify(exactly = 1) { api.getForecast(any(), any(), any(), any()) }
    }

    @Test
    fun `past the floor the request is conditional and a 304 reuses the body and restarts the floor`() = runTest {
        val api = mockk<MetNorwayApi>()
        val cache = cache()
        cache.put(NY_LAT, NY_LON, entryExpiringIn4Minutes())
        val notModified = okhttp3.Response.Builder()
            .code(304)
            .message("Not Modified")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://api.met.no/test").build())
            .headers(headersOf("Expires", "Sun, 15 Nov 2026 09:45:00 GMT"))
            .build()
        coEvery { api.getForecast(any(), any(), any(), any()) } returns
            Response.error("".toByteArray().toResponseBody(null), notModified)
        val adapter = MetNorwayForecastAdapter(api, cache)

        now = t0.plus(Duration.ofHours(3).plusMinutes(1))
        val first = adapter.getWeather(NY_LAT, NY_LON, "New York").getOrThrow()
        assertEquals(12.0, first.current.temperature, 0.01)
        coVerify(exactly = 1) {
            api.getForecast(NY_LAT, NY_LON, any(), "Sun, 15 Nov 2026 06:30:00 GMT")
        }

        // The 304 counts as a fresh fetch, so the next load inside 3 hours stays offline.
        now = now.plus(Duration.ofHours(2).plusMinutes(59))
        adapter.getWeather(NY_LAT, NY_LON, "New York").getOrThrow()
        coVerify(exactly = 1) { api.getForecast(any(), any(), any(), any()) }
    }

    private companion object {
        const val OSLO_LAT = 59.91
        const val OSLO_LON = 10.75
        const val NY_LAT = 40.71
        const val NY_LON = -74.01
    }
}
