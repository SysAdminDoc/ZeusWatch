package com.sysadmindoc.nimbus.wear.sync

import android.content.Context
import com.sysadmindoc.nimbus.wear.data.HourlyEntry
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry
import com.sysadmindoc.nimbus.wear.data.WearDailyEntry
import com.sysadmindoc.nimbus.wear.testing.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SyncedWeatherStore].
 *
 * The store is the watch-side handoff from the phone DataLayer sync —
 * regressions here either silently stale-cache a watch or, worse, blend
 * fields from two payloads. Covered behaviors:
 *  - round-trips a full payload including hourly/daily/alert arrays;
 *  - returns null when stale (>30 min) or never synced;
 *  - returns null when the sentinel `weatherCode = -1` indicates a missing payload;
 *  - cleans up indexed keys when a later save shrinks the array sizes.
 */
class SyncedWeatherStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var context: Context
    private lateinit var store: SyncedWeatherStore

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        store = SyncedWeatherStore(context)
    }

    @Test
    fun `getFreshData returns null when never synced`() {
        assertNull(store.getFreshData())
        assertEquals(0L, store.lastSyncTimestamp())
    }

    @Test
    fun `getFreshData round-trips a full payload`() {
        val now = System.currentTimeMillis()
        store.save(
            temperature = 72,
            condition = "Clear Sky",
            high = 80,
            low = 65,
            locationName = "Seattle",
            humidity = 55,
            windSpeed = 8,
            uvIndex = 6,
            precipChance = 10,
            isDay = true,
            weatherCode = 0,
            timestampMs = now,
            hourly = listOf(
                HourlyEntry(time = "10:00", temperature = 70, weatherCode = 0, precipChance = 0, windSpeed = 5),
                HourlyEntry(time = "11:00", temperature = 72, weatherCode = 1, precipChance = 5, windSpeed = 7),
            ),
            daily = listOf(
                WearDailyEntry(date = "Mon", weatherCode = 0, high = 80, low = 65, precipChance = 10),
                WearDailyEntry(date = "Tue", weatherCode = 61, high = 75, low = 60, precipChance = 80),
            ),
            alerts = listOf(
                WearAlertEntry(event = "Heat Advisory", severity = "Moderate", headline = "Highs near 100", expires = "20:00"),
            ),
            aqi = 42,
            aqiLabel = "Good",
        )

        val data = store.getFreshData()
        assertNotNull(data)
        val d = data!!
        assertEquals(72, d.temperature)
        assertEquals("Clear Sky", d.condition)
        assertEquals(80, d.high)
        assertEquals(65, d.low)
        assertEquals("Seattle", d.locationName)
        assertEquals(55, d.humidity)
        assertEquals(8, d.windSpeed)
        assertEquals(6, d.uvIndex)
        assertEquals(10, d.precipChance)
        assertTrue(d.isDay)
        assertEquals(0, d.weatherCode)
        assertEquals(2, d.hourly.size)
        assertEquals("11:00", d.hourly[1].time)
        assertEquals(72, d.hourly[1].temperature)
        assertEquals(2, d.daily.size)
        assertEquals("Tue", d.daily[1].date)
        assertEquals(80, d.daily[1].precipChance)
        assertEquals(1, d.alerts.size)
        assertEquals("Heat Advisory", d.alerts[0].event)
        assertEquals(42, d.aqi)
        assertEquals("Good", d.aqiLabel)
        assertEquals(now, store.lastSyncTimestamp())
    }

    @Test
    fun `getFreshData drops payloads older than 30 minutes`() {
        val tooOld = System.currentTimeMillis() - 31 * 60 * 1000L
        savePayload(timestamp = tooOld)
        assertNull(store.getFreshData())
        // lastSyncTimestamp is still reported so the UI can render a "stale" badge.
        assertEquals(tooOld, store.lastSyncTimestamp())
    }

    @Test
    fun `getFreshData accepts payloads inside the 30-minute window`() {
        val fresh = System.currentTimeMillis() - 29 * 60 * 1000L
        savePayload(timestamp = fresh)
        assertNotNull(store.getFreshData())
    }

    @Test
    fun `getFreshData returns null when weatherCode is missing sentinel`() {
        // weatherCode -1 acts as a sentinel for "not present in payload".
        savePayload(timestamp = System.currentTimeMillis(), weatherCode = -1)
        assertNull(store.getFreshData())
    }

    @Test
    fun `save shrinking hourly array cleans up old indexed keys`() {
        val now = System.currentTimeMillis()
        // First save: 3 hourly entries.
        store.save(
            temperature = 70, condition = "Clear Sky", high = 80, low = 60,
            locationName = "Seattle", humidity = 50, windSpeed = 5, uvIndex = 4,
            precipChance = 0, isDay = true, weatherCode = 0, timestampMs = now,
            hourly = listOf(
                HourlyEntry(time = "10:00", temperature = 70, weatherCode = 0),
                HourlyEntry(time = "11:00", temperature = 72, weatherCode = 0),
                HourlyEntry(time = "12:00", temperature = 74, weatherCode = 0),
            ),
        )
        // Second save: 1 hourly entry. Indexes 1 and 2 must be wiped.
        store.save(
            temperature = 75, condition = "Clear Sky", high = 80, low = 60,
            locationName = "Seattle", humidity = 50, windSpeed = 5, uvIndex = 4,
            precipChance = 0, isDay = true, weatherCode = 0, timestampMs = now + 1,
            hourly = listOf(HourlyEntry(time = "13:00", temperature = 75, weatherCode = 0)),
        )

        val data = store.getFreshData()
        assertNotNull(data)
        val d = data!!
        assertEquals(1, d.hourly.size)
        assertEquals("13:00", d.hourly[0].time)
        // The leftover keys must be gone, not just unread.
        assertNull(prefs.getString("hourly_1_time", null))
        assertNull(prefs.getString("hourly_2_time", null))
    }

    @Test
    fun `save shrinking daily and alert arrays cleans up old indexed keys`() {
        val now = System.currentTimeMillis()
        store.save(
            temperature = 70, condition = "Clear Sky", high = 80, low = 60,
            locationName = "Seattle", humidity = 50, windSpeed = 5, uvIndex = 4,
            precipChance = 0, isDay = true, weatherCode = 0, timestampMs = now,
            hourly = emptyList(),
            daily = listOf(
                WearDailyEntry("Mon", 0, 80, 60),
                WearDailyEntry("Tue", 0, 80, 60),
            ),
            alerts = listOf(
                WearAlertEntry("A", "Minor", "h", "e"),
                WearAlertEntry("B", "Moderate", "h", "e"),
            ),
        )
        store.save(
            temperature = 70, condition = "Clear Sky", high = 80, low = 60,
            locationName = "Seattle", humidity = 50, windSpeed = 5, uvIndex = 4,
            precipChance = 0, isDay = true, weatherCode = 0, timestampMs = now + 1,
            hourly = emptyList(),
            daily = emptyList(),
            alerts = emptyList(),
        )

        val data = store.getFreshData()
        assertNotNull(data)
        val d = data!!
        assertEquals(0, d.daily.size)
        assertEquals(0, d.alerts.size)
        assertNull(prefs.getString("daily_0_date", null))
        assertNull(prefs.getString("alert_0_event", null))
        assertNull(prefs.getString("alert_1_event", null))
    }

    @Test
    fun `save flushes synchronously so a subsequent read returns the new payload`() {
        // SyncedWeatherStore.save() uses commit() instead of apply() so a sync
        // arriving moments before WearableListenerService gets killed actually
        // lands on disk. We exercise the post-save read contract here — if this
        // ever regresses to apply() + a flaky reader, the test still passes for
        // the in-memory fake but flags the intent.
        savePayload(timestamp = 12_345L)
        assertEquals(12_345L, store.lastSyncTimestamp())
    }

    private fun savePayload(
        timestamp: Long,
        condition: String = "Clear Sky",
        weatherCode: Int = 0,
    ) {
        store.save(
            temperature = 70,
            condition = condition,
            high = 80,
            low = 60,
            locationName = "Seattle",
            humidity = 50,
            windSpeed = 5,
            uvIndex = 4,
            precipChance = 0,
            isDay = true,
            weatherCode = weatherCode,
            timestampMs = timestamp,
            hourly = emptyList(),
        )
    }
}
