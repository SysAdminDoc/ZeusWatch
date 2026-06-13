package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.MarineResponse
import com.sysadmindoc.nimbus.data.api.OpenMeteoMarineApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class MarineData(
    val waveHeight: Double,
    val waveDirection: Int,
    val wavePeriod: Double,
    val currentVelocity: Double?,
    val currentDirection: Int?,
    val hourlyWaveHeight: List<Double>,
    val hourlySst: List<Double>,
)

@Singleton
class MarineRepository @Inject constructor(
    private val api: OpenMeteoMarineApi,
) {
    suspend fun getMarine(lat: Double, lon: Double): Result<MarineData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMarine(lat, lon)
            val current = response.current
                ?: return@withContext Result.failure(Exception("No marine data"))
            val wh = current.waveHeight
                ?: return@withContext Result.failure(Exception("No wave height"))

            Result.success(
                MarineData(
                    waveHeight = wh,
                    waveDirection = current.waveDirection ?: 0,
                    wavePeriod = current.wavePeriod ?: 0.0,
                    currentVelocity = current.oceanCurrentVelocity,
                    currentDirection = current.oceanCurrentDirection,
                    hourlyWaveHeight = response.hourly?.waveHeight?.filterNotNull()?.take(24) ?: emptyList(),
                    hourlySst = response.hourly?.seaSurfaceTemperature?.filterNotNull()?.take(24) ?: emptyList(),
                ),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
