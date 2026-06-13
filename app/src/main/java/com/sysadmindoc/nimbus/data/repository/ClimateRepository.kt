package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoClimateApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ClimateOutlookData(
    val baselineAvgHigh: Double,
    val baselineAvgLow: Double,
    val projectedAvgHigh: Double,
    val projectedAvgLow: Double,
    val projectedAvgPrecip: Double,
    val baselineAvgPrecip: Double,
) {
    val highDelta: Double get() = projectedAvgHigh - baselineAvgHigh
    val lowDelta: Double get() = projectedAvgLow - baselineAvgLow
    val precipDelta: Double get() = projectedAvgPrecip - baselineAvgPrecip
}

@Singleton
class ClimateRepository @Inject constructor(
    private val api: OpenMeteoClimateApi,
) {
    suspend fun getClimateOutlook(lat: Double, lon: Double): Result<ClimateOutlookData> =
        withContext(Dispatchers.IO) {
            try {
                val baseline = api.getClimate(
                    latitude = lat,
                    longitude = lon,
                    startDate = BASELINE_START,
                    endDate = BASELINE_END,
                )
                val projection = api.getClimate(
                    latitude = lat,
                    longitude = lon,
                    startDate = PROJECTION_START,
                    endDate = PROJECTION_END,
                )

                val baseHighs = baseline.daily?.temperatureMax?.filterNotNull() ?: emptyList()
                val baseLows = baseline.daily?.temperatureMin?.filterNotNull() ?: emptyList()
                val basePrecip = baseline.daily?.precipitationSum?.filterNotNull() ?: emptyList()
                val projHighs = projection.daily?.temperatureMax?.filterNotNull() ?: emptyList()
                val projLows = projection.daily?.temperatureMin?.filterNotNull() ?: emptyList()
                val projPrecip = projection.daily?.precipitationSum?.filterNotNull() ?: emptyList()

                if (baseHighs.isEmpty() || projHighs.isEmpty()) {
                    return@withContext Result.failure(Exception("Insufficient climate data"))
                }

                Result.success(
                    ClimateOutlookData(
                        baselineAvgHigh = baseHighs.average(),
                        baselineAvgLow = if (baseLows.isNotEmpty()) baseLows.average() else baseHighs.average(),
                        projectedAvgHigh = projHighs.average(),
                        projectedAvgLow = if (projLows.isNotEmpty()) projLows.average() else projHighs.average(),
                        projectedAvgPrecip = if (projPrecip.isNotEmpty()) projPrecip.average() else 0.0,
                        baselineAvgPrecip = if (basePrecip.isNotEmpty()) basePrecip.average() else 0.0,
                    ),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }

    companion object {
        private const val BASELINE_START = "1950-01-01"
        private const val BASELINE_END = "1980-12-31"
        private const val PROJECTION_START = "2040-01-01"
        private const val PROJECTION_END = "2060-12-31"
    }
}
