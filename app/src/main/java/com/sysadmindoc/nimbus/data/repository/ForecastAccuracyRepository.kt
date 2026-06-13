package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoPreviousRunsApi
import com.sysadmindoc.nimbus.data.model.DailyWeather
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Singleton
class ForecastAccuracyRepository @Inject constructor(
    private val api: OpenMeteoPreviousRunsApi,
) {
    suspend fun getForecastAccuracy(
        latitude: Double,
        longitude: Double,
    ): Result<ForecastAccuracyData> = runCatching {
        coroutineScope {
            val day1 = async { api.getPreviousRun(latitude, longitude, previousDay = 1) }
            val day3 = async { api.getPreviousRun(latitude, longitude, previousDay = 3) }
            val day7 = async { api.getPreviousRun(latitude, longitude, previousDay = 7) }

            val resp1 = day1.await()
            val resp3 = day3.await()
            val resp7 = day7.await()

            val predictions1 = parseDailyPredictions(resp1.daily, 1)
            val predictions3 = parseDailyPredictions(resp3.daily, 3)
            val predictions7 = parseDailyPredictions(resp7.daily, 7)

            val byDate = mutableMapOf<LocalDate, MutableList<DailyPrediction>>()
            for (p in predictions1 + predictions3 + predictions7) {
                byDate.getOrPut(p.date) { mutableListOf() }.add(p)
            }

            ForecastAccuracyData(
                predictionsByDate = byDate.mapValues { it.value.toList() },
            )
        }
    }
}

data class ForecastAccuracyData(
    val predictionsByDate: Map<LocalDate, List<DailyPrediction>>,
) {
    fun bestPredictionDelta(date: LocalDate, observedHigh: Double): ForecastDelta? {
        val predictions = predictionsByDate[date] ?: return null
        val nearest = predictions.minByOrNull { it.leadDays } ?: return null
        val delta = nearest.predictedHigh - observedHigh
        return ForecastDelta(
            leadDays = nearest.leadDays,
            temperatureDeltaC = delta,
        )
    }
}

data class DailyPrediction(
    val date: LocalDate,
    val leadDays: Int,
    val predictedHigh: Double,
    val predictedLow: Double,
    val predictedPrecipSum: Double,
)

data class ForecastDelta(
    val leadDays: Int,
    val temperatureDeltaC: Double,
)

internal fun parseDailyPredictions(
    daily: DailyWeather?,
    leadDays: Int,
): List<DailyPrediction> {
    daily ?: return emptyList()
    return daily.time.mapIndexedNotNull { index, rawDate ->
        val date = runCatching { LocalDate.parse(rawDate, DATE_FORMAT) }.getOrNull()
            ?: return@mapIndexedNotNull null
        val high = daily.temperatureMax?.getOrNull(index)
            ?: return@mapIndexedNotNull null
        val low = daily.temperatureMin?.getOrNull(index)
            ?: return@mapIndexedNotNull null
        val precipSum = daily.precipitationSum?.getOrNull(index) ?: 0.0
        DailyPrediction(
            date = date,
            leadDays = leadDays,
            predictedHigh = high,
            predictedLow = low,
            predictedPrecipSum = precipSum,
        )
    }
}
