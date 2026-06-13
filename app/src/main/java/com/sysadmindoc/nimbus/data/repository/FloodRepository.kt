package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoFloodApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class FloodData(
    val currentDischarge: Double,
    val meanDischarge: Double,
    val maxDischarge: Double,
    val riskLevel: FloodRiskLevel,
    val dailyDischarge: List<Double>,
)

enum class FloodRiskLevel(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
    EXTREME("Extreme");

    companion object {
        fun from(current: Double, mean: Double): FloodRiskLevel {
            if (mean <= 0) return LOW
            val ratio = current / mean
            return when {
                ratio > 5.0 -> EXTREME
                ratio > 3.0 -> HIGH
                ratio > 1.5 -> MODERATE
                else -> LOW
            }
        }
    }
}

@Singleton
class FloodRepository @Inject constructor(
    private val api: OpenMeteoFloodApi,
) {
    suspend fun getFlood(lat: Double, lon: Double): Result<FloodData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getFlood(lat, lon)
            val daily = response.daily
                ?: return@withContext Result.failure(Exception("No flood data"))
            val discharges = daily.riverDischarge?.filterNotNull() ?: emptyList()
            val means = daily.riverDischargeMean?.filterNotNull() ?: emptyList()
            val maxes = daily.riverDischargeMax?.filterNotNull() ?: emptyList()

            if (discharges.isEmpty()) {
                return@withContext Result.failure(Exception("No river discharge data"))
            }

            val current = discharges.first()
            val mean = means.firstOrNull() ?: current
            val max = maxes.firstOrNull() ?: current

            Result.success(
                FloodData(
                    currentDischarge = current,
                    meanDischarge = mean,
                    maxDischarge = max,
                    riskLevel = FloodRiskLevel.from(current, mean),
                    dailyDischarge = discharges.take(30),
                ),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
