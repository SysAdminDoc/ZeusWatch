package com.sysadmindoc.nimbus.wear.sync

import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDataListenerServiceTest {

    @Test
    fun `toSyncedWeatherPayload bounds incoming arrays and scalar values`() {
        val map = DataMap().apply {
            putInt("temperature", 150)
            putString("condition", "  ${"Storm".repeat(30)}  ")
            putInt("high", 125)
            putInt("low", -150)
            putString("locationName", "  ${"Seattle".repeat(20)}  ")
            putInt("humidity", 150)
            putInt("windSpeed", 900)
            putInt("uvIndex", 40)
            putInt("precipChance", -25)
            putInt("weatherCode", 999)
            putInt("aqi", 900)
            putString("aqiLabel", "  ${"Unhealthy".repeat(20)}  ")
            putDataMapArrayList("hourly", dataMaps(20) { i ->
                putString("time", "2026-06-11T${i.toString().padStart(2, '0')}:00")
                putInt("temperature", -150)
                putInt("weatherCode", 999)
                putInt("precipChance", 120)
                putInt("windSpeed", 900)
            })
            putDataMapArrayList("daily", dataMaps(14) { i ->
                putString("date", "day-$i")
                putInt("weatherCode", 999)
                putInt("high", 125)
                putInt("low", -150)
                putInt("precipChance", 120)
            })
            putDataMapArrayList("alerts", dataMaps(20) { i ->
                putString("event", "Event-$i-${"x".repeat(200)}")
                putString("severity", "  Extreme  ")
                putString("headline", "Headline-$i-${"y".repeat(400)}")
                putString("expires", "2026-06-11T12:00:00Z")
            })
        }

        val payload = map.toSyncedWeatherPayload(receivedAtMs = 1_000L)

        assertEquals(100, payload.temperature)
        assertEquals(80, payload.condition.length)
        assertEquals(100, payload.high)
        assertEquals(-100, payload.low)
        assertEquals(80, payload.locationName.length)
        assertEquals(100, payload.humidity)
        assertEquals(500, payload.windSpeed)
        assertEquals(25, payload.uvIndex)
        assertEquals(0, payload.precipChance)
        assertEquals(99, payload.weatherCode)
        assertEquals(500, payload.aqi)
        assertEquals(80, payload.aqiLabel!!.length)

        assertEquals(12, payload.hourly.size)
        assertEquals(-100, payload.hourly.first().temperature)
        assertEquals(99, payload.hourly.first().weatherCode)
        assertEquals(100, payload.hourly.first().precipChance)
        assertEquals(500, payload.hourly.first().windSpeed)

        assertEquals(7, payload.daily.size)
        assertEquals(100, payload.daily.first().high)
        assertEquals(-100, payload.daily.first().low)
        assertEquals(100, payload.daily.first().precipChance)

        val alerts = payload.alerts!!
        assertEquals(8, alerts.size)
        assertTrue(alerts.first().event.length <= 120)
        assertEquals("Extreme", alerts.first().severity)
        assertEquals(240, alerts.first().headline.length)
    }

    @Test
    fun `toSyncedWeatherPayload reports absent alerts and aqi as unknown`() {
        // A background sync that didn't fetch alerts/AQI omits the keys —
        // the payload must say "unknown" (null) so the store preserves the
        // previously synced values instead of wiping them every 15 minutes.
        val payload = DataMap().toSyncedWeatherPayload(receivedAtMs = 1_000L)

        assertNull(payload.alerts)
        assertNull(payload.aqi)
        assertNull(payload.aqiLabel)
    }

    @Test
    fun `toSyncedWeatherPayload keeps an explicit empty alert list as cleared`() {
        val payload = DataMap().apply {
            putDataMapArrayList("alerts", arrayListOf())
        }.toSyncedWeatherPayload(receivedAtMs = 1_000L)

        assertEquals(0, payload.alerts!!.size)
    }

    @Test
    fun `toSyncedWeatherPayload defaults units to metric when keys are absent`() {
        val payload = DataMap().toSyncedWeatherPayload(receivedAtMs = 1_000L)

        assertEquals("CELSIUS", payload.tempUnit)
        assertEquals("KMH", payload.windUnit)
    }

    @Test
    fun `toSyncedWeatherPayload carries the phone display units`() {
        val payload = DataMap().apply {
            putString("tempUnit", "FAHRENHEIT")
            putString("windUnit", "MPH")
        }.toSyncedWeatherPayload(receivedAtMs = 1_000L)

        assertEquals("FAHRENHEIT", payload.tempUnit)
        assertEquals("MPH", payload.windUnit)
    }

    @Test
    fun `toSyncedWeatherPayload parses hourly isDay and defaults to day when absent`() {
        val payload = DataMap().apply {
            putDataMapArrayList("hourly", dataMaps(2) { i ->
                putString("time", "2026-06-11T0$i:00")
                putInt("temperature", 10)
                if (i == 0) putBoolean("isDay", false)
            })
        }.toSyncedWeatherPayload(receivedAtMs = 1_000L)

        assertFalse(payload.hourly[0].isDay)
        assertTrue(payload.hourly[1].isDay)
    }

    @Test
    fun `toSyncedWeatherPayload clamps invalid or far future timestamps to receive time`() {
        val receivedAt = 10_000L

        val missing = DataMap().apply {
            putLong("syncTimestampMs", 0L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt, missing.timestampMs)

        val tooFarFuture = DataMap().apply {
            putLong("syncTimestampMs", receivedAt + 10 * 60 * 1000L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt, tooFarFuture.timestampMs)

        val smallClockSkew = DataMap().apply {
            putLong("syncTimestampMs", receivedAt + 60 * 1000L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt + 60 * 1000L, smallClockSkew.timestampMs)
    }

    @Test
    fun `toSyncedWeatherPayload clamps far past timestamps from a slow phone clock`() {
        val receivedAt = 60 * 60 * 1000L

        // A phone clock running >5 min behind must not make every sync look
        // instantly stale — clamp up to the watch's receive time.
        val tooFarPast = DataMap().apply {
            putLong("syncTimestampMs", receivedAt - 10 * 60 * 1000L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt, tooFarPast.timestampMs)

        // Small backwards skew within the allowance is preserved.
        val smallPastSkew = DataMap().apply {
            putLong("syncTimestampMs", receivedAt - 60 * 1000L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt - 60 * 1000L, smallPastSkew.timestampMs)
    }

    @Test
    fun `toSyncedWeatherPayload preserves an honest past data age`() {
        val receivedAt = 24 * 60 * 60 * 1000L
        // The phone deliberately pushes hours-old cached data during outages.
        // Unlike syncTimestampMs, that age must survive — no past clamp.
        val sixHoursOld = receivedAt - 6 * 60 * 60 * 1000L

        val payload = DataMap().apply {
            putLong("syncTimestampMs", receivedAt)
            putLong("dataUpdatedAtMs", sixHoursOld)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)

        assertEquals(receivedAt, payload.timestampMs)
        assertEquals(sixHoursOld, payload.dataUpdatedAtMs)
    }

    @Test
    fun `toSyncedWeatherPayload rejects absurd data age values`() {
        val receivedAt = 10_000L

        // Far-future value → fall back to the sanitized sync timestamp.
        val farFuture = DataMap().apply {
            putLong("syncTimestampMs", receivedAt)
            putLong("dataUpdatedAtMs", receivedAt + 10 * 60 * 1000L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt, farFuture.dataUpdatedAtMs)

        // Non-positive value → same fallback.
        val nonPositive = DataMap().apply {
            putLong("syncTimestampMs", receivedAt)
            putLong("dataUpdatedAtMs", 0L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)
        assertEquals(receivedAt, nonPositive.dataUpdatedAtMs)
    }

    @Test
    fun `toSyncedWeatherPayload defaults data age to sync time when the field is absent`() {
        // Older phone apps don't send dataUpdatedAtMs — behavior must match
        // the pre-field protocol (freshness keyed off the sync timestamp).
        val receivedAt = 10_000L
        val payload = DataMap().apply {
            putLong("syncTimestampMs", receivedAt - 60 * 1000L)
        }.toSyncedWeatherPayload(receivedAtMs = receivedAt)

        assertEquals(payload.timestampMs, payload.dataUpdatedAtMs)
    }

    private fun dataMaps(
        count: Int,
        block: DataMap.(Int) -> Unit,
    ): ArrayList<DataMap> = ArrayList<DataMap>().apply {
        repeat(count) { index ->
            add(DataMap().apply { block(index) })
        }
    }
}
