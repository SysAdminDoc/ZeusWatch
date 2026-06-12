package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoSingleRunApi
import com.sysadmindoc.nimbus.data.model.HourlyWeather
import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class ForecastEvolutionRepositoryTest {

    @Test
    fun candidateLatestRunsUsesSafeAvailabilityLagAndSixHourCycles() {
        val runs = candidateLatestRuns(Instant.parse("2026-06-12T14:10:00Z"))

        assertEquals(
            listOf(
                LocalDateTime.parse("2026-06-12T06:00:00"),
                LocalDateTime.parse("2026-06-12T00:00:00"),
                LocalDateTime.parse("2026-06-11T18:00:00"),
                LocalDateTime.parse("2026-06-11T12:00:00"),
            ),
            runs,
        )
    }

    @Test
    fun comparisonPointsAlignsMatchingFutureHours() {
        val latest = hourly(
            times = listOf("2026-06-12T13:00", "2026-06-12T14:00", "2026-06-12T20:00", "2026-06-13T02:00"),
            temperatures = listOf(20.0, 21.0, 19.0, 18.0),
            precipitation = listOf(10, 20, 60, 40),
        )
        val previous = hourly(
            times = listOf("2026-06-12T14:00", "2026-06-12T20:00", "2026-06-13T02:00"),
            temperatures = listOf(19.0, 22.0, 17.0),
            precipitation = listOf(30, 50, 45),
        )

        val points = comparisonPoints(
            latest = latest,
            previous = previous,
            localNow = LocalDateTime.parse("2026-06-12T14:00:00"),
        )

        assertEquals(3, points.size)
        assertEquals(LocalDateTime.parse("2026-06-12T14:00:00"), points[0].time)
        assertEquals(2.0, points[0].temperatureDeltaC, 0.001)
        assertEquals(-5, points[2].precipitationProbabilityDelta)
    }

    @Test
    fun getForecastEvolutionFallsBackToOlderRunPairWhenLatestPairUnavailable() = runTest {
        val api = mockk<OpenMeteoSingleRunApi>()
        val repository = ForecastEvolutionRepository(api)
        coEvery {
            api.getForecastRun(any(), any(), "2026-06-12T06:00", any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("run unavailable")
        coEvery {
            api.getForecastRun(any(), any(), "2026-06-12T00:00", any(), any(), any(), any(), any(), any())
        } returns response(temperatureOffset = 1.0)
        coEvery {
            api.getForecastRun(any(), any(), "2026-06-11T18:00", any(), any(), any(), any(), any(), any())
        } returns response(temperatureOffset = -1.0)

        val result = repository.getForecastEvolution(
            latitude = 39.7,
            longitude = -104.9,
            now = Instant.parse("2026-06-12T14:10:00Z"),
        )

        assertTrue(result.isSuccess)
        assertEquals(LocalDateTime.parse("2026-06-12T00:00:00"), result.getOrThrow().latestRun)
        assertEquals(2.0, result.getOrThrow().points.first().temperatureDeltaC, 0.001)
        coVerify {
            api.getForecastRun(39.7, -104.9, "2026-06-12T06:00", any(), any(), any(), any(), any(), any())
            api.getForecastRun(39.7, -104.9, "2026-06-12T00:00", any(), any(), any(), any(), any(), any())
            api.getForecastRun(39.7, -104.9, "2026-06-11T18:00", any(), any(), any(), any(), any(), any())
        }
    }

    private fun response(temperatureOffset: Double): OpenMeteoResponse {
        return OpenMeteoResponse(
            latitude = 39.7,
            longitude = -104.9,
            hourly = hourly(
                times = listOf("2026-06-12T14:00", "2026-06-12T20:00", "2026-06-13T02:00"),
                temperatures = listOf(20.0 + temperatureOffset, 19.0 + temperatureOffset, 18.0 + temperatureOffset),
                precipitation = listOf(20, 40, 60),
            ),
        )
    }

    private fun hourly(
        times: List<String>,
        temperatures: List<Double>,
        precipitation: List<Int>,
    ): HourlyWeather {
        return HourlyWeather(
            time = times,
            temperature = temperatures,
            precipitationProbability = precipitation,
        )
    }
}
