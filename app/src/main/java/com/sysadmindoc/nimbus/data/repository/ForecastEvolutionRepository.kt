package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoSingleRunApi
import com.sysadmindoc.nimbus.data.model.HourlyWeather
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private val RUN_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private const val RUN_AVAILABILITY_LAG_HOURS = 8L
private const val RUN_INTERVAL_HOURS = 6L
private const val MAX_RUN_PAIR_ATTEMPTS = 4

@Singleton
class ForecastEvolutionRepository @Inject constructor(
    private val api: OpenMeteoSingleRunApi,
) {
    suspend fun getForecastEvolution(
        latitude: Double,
        longitude: Double,
        now: Instant = Instant.now(),
    ): Result<ForecastEvolutionData> {
        var lastError: Throwable? = null
        for (latestRun in candidateLatestRuns(now)) {
            val previousRun = latestRun.minusHours(RUN_INTERVAL_HOURS)
            val result = fetchRunPair(latitude, longitude, latestRun, previousRun, now)
            result.fold(
                onSuccess = { return Result.success(it) },
                onFailure = { lastError = it },
            )
        }
        return Result.failure(lastError ?: IllegalStateException("No comparable forecast runs were available."))
    }

    private suspend fun fetchRunPair(
        latitude: Double,
        longitude: Double,
        latestRun: LocalDateTime,
        previousRun: LocalDateTime,
        now: Instant,
    ): Result<ForecastEvolutionData> = runCatching {
        coroutineScope {
            val latest = async {
                api.getForecastRun(latitude, longitude, latestRun.format(RUN_FORMATTER))
            }
            val previous = async {
                api.getForecastRun(latitude, longitude, previousRun.format(RUN_FORMATTER))
            }
            val latestResponse = latest.await()
            val previousResponse = previous.await()
            val localNow = LocalDateTime.ofInstant(
                now,
                ZoneOffset.ofTotalSeconds(latestResponse.utcOffsetSeconds),
            )
            ForecastEvolutionData(
                latestRun = latestRun,
                previousRun = previousRun,
                modelName = "ICON Global",
                points = comparisonPoints(
                    latest = latestResponse.hourly,
                    previous = previousResponse.hourly,
                    localNow = localNow,
                ),
            ).also { data ->
                require(data.points.isNotEmpty()) { "No overlapping forecast hours were available." }
            }
        }
    }
}

data class ForecastEvolutionData(
    val latestRun: LocalDateTime,
    val previousRun: LocalDateTime,
    val modelName: String,
    val points: List<ForecastEvolutionPoint>,
) {
    val averageTemperatureDeltaC: Double
        get() = points.map { it.temperatureDeltaC }.average()

    val averagePrecipitationProbabilityDelta: Double
        get() = points.map { it.precipitationProbabilityDelta }.average()
}

data class ForecastEvolutionPoint(
    val time: LocalDateTime,
    val latestTemperatureC: Double,
    val previousTemperatureC: Double,
    val latestPrecipitationProbability: Int,
    val previousPrecipitationProbability: Int,
) {
    val temperatureDeltaC: Double = latestTemperatureC - previousTemperatureC
    val precipitationProbabilityDelta: Int =
        latestPrecipitationProbability - previousPrecipitationProbability
}

internal fun candidateLatestRuns(now: Instant): List<LocalDateTime> {
    val delayed = LocalDateTime.ofInstant(now.minusSeconds(RUN_AVAILABILITY_LAG_HOURS * 3600), ZoneOffset.UTC)
    val latestCycleHour = (delayed.hour / RUN_INTERVAL_HOURS * RUN_INTERVAL_HOURS).toLong()
    val latest = delayed
        .withHour(latestCycleHour.toInt())
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
    return List(MAX_RUN_PAIR_ATTEMPTS) { attempt ->
        latest.minusHours(RUN_INTERVAL_HOURS * attempt)
    }
}

internal fun comparisonPoints(
    latest: HourlyWeather?,
    previous: HourlyWeather?,
    localNow: LocalDateTime,
): List<ForecastEvolutionPoint> {
    latest ?: return emptyList()
    previous ?: return emptyList()
    val latestByTime = hourlySnapshots(latest)
    val previousByTime = hourlySnapshots(previous)
    return latestByTime.keys
        .intersect(previousByTime.keys)
        .asSequence()
        .mapNotNull { time ->
            val latestPoint = latestByTime[time] ?: return@mapNotNull null
            val previousPoint = previousByTime[time] ?: return@mapNotNull null
            ForecastEvolutionPoint(
                time = time,
                latestTemperatureC = latestPoint.temperatureC,
                previousTemperatureC = previousPoint.temperatureC,
                latestPrecipitationProbability = latestPoint.precipitationProbability,
                previousPrecipitationProbability = previousPoint.precipitationProbability,
            )
        }
        .filter { !it.time.isBefore(localNow.minusHours(1)) }
        .sortedBy { it.time }
        .selectKeyForecastHours()
}

private data class HourlySnapshot(
    val temperatureC: Double,
    val precipitationProbability: Int,
)

private fun hourlySnapshots(hourly: HourlyWeather): Map<LocalDateTime, HourlySnapshot> {
    return hourly.time.mapIndexedNotNull { index, rawTime ->
        val temperature = hourly.temperature?.getOrNull(index) ?: return@mapIndexedNotNull null
        val precipitationProbability = hourly.precipitationProbability?.getOrNull(index)
            ?: return@mapIndexedNotNull null
        val time = runCatching { LocalDateTime.parse(rawTime) }.getOrNull()
            ?: return@mapIndexedNotNull null
        time to HourlySnapshot(
            temperatureC = temperature,
            precipitationProbability = precipitationProbability,
        )
    }.toMap()
}

private fun Sequence<ForecastEvolutionPoint>.selectKeyForecastHours(): List<ForecastEvolutionPoint> {
    val points = toList()
    if (points.size <= 4) return points
    val firstTime = points.first().time
    val targets = listOf(0L, 6L, 12L, 24L).map { firstTime.plusHours(it) }
    return targets.mapNotNull { target ->
        points.minByOrNull { point ->
            kotlin.math.abs(java.time.Duration.between(target, point.time).toMinutes())
        }
    }.distinctBy { it.time }
}
