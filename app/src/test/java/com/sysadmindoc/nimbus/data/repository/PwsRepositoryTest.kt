package com.sysadmindoc.nimbus.data.repository

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PwsRepositoryTest {

    @Test
    fun parseTempestObservationMapsDocumentedObsStFields() {
        val raw = """
            {
              "type":"obs_st",
              "device_id":62009,
              "obs":[[1603481377,0,0.09,0.54,33,6,1014.8,28.8,71,16639,1.83,139,0.42,1,12,3,2.42,1]]
            }
        """.trimIndent()

        val observation = TempestObservationParser.parse(raw, ZoneOffset.UTC)

        requireNotNull(observation)
        assertEquals(
            LocalDateTime.ofInstant(Instant.ofEpochSecond(1603481377), ZoneOffset.UTC),
            observation.observedAt,
        )
        assertEquals(28.8, observation.temperatureC, 0.001)
        assertEquals(71, observation.humidityPercent)
        assertEquals(0.09 * 3.6, observation.windSpeedKmh, 0.001)
        assertEquals(0.54 * 3.6, observation.windGustKmh ?: 0.0, 0.001)
        assertEquals(33, observation.windDirectionDegrees)
        assertEquals(1014.8, observation.pressureHpa ?: 0.0, 0.001)
        assertEquals(1.83, observation.uvIndex ?: 0.0, 0.001)
        assertEquals(0.42, observation.rainLastMinuteMm ?: 0.0, 0.001)
        assertEquals(TempestPrecipitationType.RAIN, observation.precipitationType)
        assertEquals(3, observation.lightningStrikeCount)
        assertEquals(12.0, observation.lightningStrikeAverageDistanceKm ?: 0.0, 0.001)
        assertEquals(1, observation.reportIntervalMinutes)
    }

    @Test
    fun parseTempestObservationIgnoresAcknowledgements() {
        assertNull(TempestObservationParser.parse("""{"type":"ack","id":"zeuswatch"}""", ZoneOffset.UTC))
    }

    @Test
    fun repositoryFailsFastWhenTempestConfigMissing() = runTest {
        val repository = PwsRepository(
            okHttpClient = OkHttpClient(),
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.fetchLatestObservation(NimbusSettings())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TempestPwsConfigurationException)
    }
}
