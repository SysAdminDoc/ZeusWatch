package com.sysadmindoc.nimbus.data.repository

import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.model.WeatherData
import java.time.LocalDateTime
import kotlin.math.abs

enum class ProviderAgreementLevel {
    STRONG,
    MODERATE,
    DIVERGENT,
}

@Stable
data class ProviderAgreementSnapshot(
    val provider: WeatherSourceProvider,
    val displayName: String,
    val averageTemperatureC: Double,
    val precipitationTotalMm: Double,
    val hourCount: Int,
)

@Stable
data class ProviderAgreementData(
    val agreement: ProviderAgreementLevel,
    val providers: List<ProviderAgreementSnapshot>,
    val temperatureSpreadC: Double,
    val precipitationSpreadMm: Double,
)

data class ProviderWeatherSnapshot(
    val provider: WeatherSourceProvider,
    val data: WeatherData,
)

object ProviderAgreementAnalyzer {
    private const val STRONG_TEMP_SPREAD_C = 1.5
    private const val STRONG_PRECIP_SPREAD_MM = 2.0
    private const val MODERATE_TEMP_SPREAD_C = 3.0
    private const val MODERATE_PRECIP_SPREAD_MM = 6.0

    fun analyze(
        forecasts: List<ProviderWeatherSnapshot>,
        referenceTime: LocalDateTime? = null,
    ): ProviderAgreementData? {
        val snapshots = forecasts
            .distinctBy { it.provider }
            .mapNotNull { forecast ->
                forecast.data.toAgreementSnapshot(
                    provider = forecast.provider,
                    referenceTime = referenceTime,
                )
            }

        if (snapshots.size < 2) return null

        val tempSpread = spreadOf(snapshots.map { it.averageTemperatureC })
        val precipSpread = spreadOf(snapshots.map { it.precipitationTotalMm })
        return ProviderAgreementData(
            agreement = agreementLevel(tempSpread, precipSpread),
            providers = snapshots,
            temperatureSpreadC = tempSpread,
            precipitationSpreadMm = precipSpread,
        )
    }

    private fun WeatherData.toAgreementSnapshot(
        provider: WeatherSourceProvider,
        referenceTime: LocalDateTime?,
    ): ProviderAgreementSnapshot? {
        val start = referenceTime
            ?: current.observationTime
            ?: hourly.firstOrNull()?.time
            ?: return null
        val end = start.plusHours(24)
        val window = hourly
            .filter { !it.time.isBefore(start) && it.time.isBefore(end) }
            .sortedBy { it.time }

        val temperatures = window.map { it.temperature }.ifEmpty { listOf(current.temperature) }
        val precipitationTotal = if (window.isEmpty()) {
            current.precipitation
        } else {
            window.sumOf { it.precipitation ?: 0.0 }
        }

        return ProviderAgreementSnapshot(
            provider = provider,
            displayName = provider.displayName,
            averageTemperatureC = temperatures.average(),
            precipitationTotalMm = precipitationTotal,
            hourCount = window.size.takeIf { it > 0 } ?: 1,
        )
    }

    private fun agreementLevel(tempSpreadC: Double, precipitationSpreadMm: Double): ProviderAgreementLevel = when {
        tempSpreadC <= STRONG_TEMP_SPREAD_C && precipitationSpreadMm <= STRONG_PRECIP_SPREAD_MM ->
            ProviderAgreementLevel.STRONG
        tempSpreadC <= MODERATE_TEMP_SPREAD_C && precipitationSpreadMm <= MODERATE_PRECIP_SPREAD_MM ->
            ProviderAgreementLevel.MODERATE
        else -> ProviderAgreementLevel.DIVERGENT
    }

    private fun spreadOf(values: List<Double>): Double {
        val min = values.minOrNull() ?: return 0.0
        val max = values.maxOrNull() ?: return 0.0
        return abs(max - min)
    }
}
