package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.NoaaSwpcApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class AuroraKpData(
    val kpValue: Double,
    val timestamp: String,
) {
    val level: KpLevel get() = KpLevel.from(kpValue)
}

enum class KpLevel(val label: String, val minKp: Double) {
    QUIET("Quiet", 0.0),
    UNSETTLED("Unsettled", 3.0),
    ACTIVE("Active", 4.0),
    MINOR_STORM("Minor Storm (G1)", 5.0),
    MODERATE_STORM("Moderate Storm (G2)", 6.0),
    STRONG_STORM("Strong Storm (G3)", 7.0),
    SEVERE_STORM("Severe Storm (G4)", 8.0),
    EXTREME_STORM("Extreme Storm (G5)", 9.0);

    companion object {
        fun from(kp: Double): KpLevel = entries.lastOrNull { kp >= it.minKp } ?: QUIET
    }
}

@Singleton
class AuroraRepository @Inject constructor(
    private val api: NoaaSwpcApi,
) {
    suspend fun getKpIndex(): Result<AuroraKpData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getKpIndex()
            if (response.size < 2) return@withContext Result.failure(Exception("Empty Kp response"))
            val latest = response.last().jsonArray
            val timestamp = latest[0].jsonPrimitive.content
            val kp = latest[1].jsonPrimitive.content.toDoubleOrNull()
                ?: return@withContext Result.failure(Exception("Invalid Kp value"))
            Result.success(AuroraKpData(kpValue = kp, timestamp = timestamp))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
