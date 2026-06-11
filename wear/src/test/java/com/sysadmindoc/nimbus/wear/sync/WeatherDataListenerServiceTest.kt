package com.sysadmindoc.nimbus.wear.sync

import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertEquals
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
        assertEquals(80, payload.aqiLabel.length)

        assertEquals(12, payload.hourly.size)
        assertEquals(-100, payload.hourly.first().temperature)
        assertEquals(99, payload.hourly.first().weatherCode)
        assertEquals(100, payload.hourly.first().precipChance)
        assertEquals(500, payload.hourly.first().windSpeed)

        assertEquals(7, payload.daily.size)
        assertEquals(100, payload.daily.first().high)
        assertEquals(-100, payload.daily.first().low)
        assertEquals(100, payload.daily.first().precipChance)

        assertEquals(8, payload.alerts.size)
        assertTrue(payload.alerts.first().event.length <= 120)
        assertEquals("Extreme", payload.alerts.first().severity)
        assertEquals(240, payload.alerts.first().headline.length)
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

    private fun dataMaps(
        count: Int,
        block: DataMap.(Int) -> Unit,
    ): ArrayList<DataMap> = ArrayList<DataMap>().apply {
        repeat(count) { index ->
            add(DataMap().apply { block(index) })
        }
    }
}
