package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoEnsembleApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfidenceBandRepository @Inject constructor(
    private val api: OpenMeteoEnsembleApi,
) {
    suspend fun getConfidenceBands(
        latitude: Double,
        longitude: Double,
    ): Result<ConfidenceBandData> = runCatching {
        val response = api.getEnsemble(latitude, longitude)
        val hourly = response.hourly ?: return@runCatching ConfidenceBandData(emptyList())
        parseEnsembleHourly(hourly)
    }
}

data class ConfidenceBandData(
    val entries: List<ConfidenceBandEntry>,
) {
    private val byTime: Map<LocalDateTime, ConfidenceBandEntry> by lazy {
        entries.associateBy { it.time }
    }

    fun entryAt(time: LocalDateTime): ConfidenceBandEntry? = byTime[time]
}

data class ConfidenceBandEntry(
    val time: LocalDateTime,
    val temperatureMean: Double,
    val temperatureLower: Double,
    val temperatureUpper: Double,
)

private val ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME

internal fun parseEnsembleHourly(hourly: JsonObject): ConfidenceBandData {
    val timeArray = (hourly["time"] as? JsonArray) ?: return ConfidenceBandData(emptyList())
    val times = timeArray.mapNotNull { elem ->
        val raw = (elem as? JsonPrimitive)?.content ?: return@mapNotNull null
        runCatching { LocalDateTime.parse(raw, ISO_LOCAL) }.getOrNull()
    }
    if (times.isEmpty()) return ConfidenceBandData(emptyList())

    val memberArrays = hourly.entries
        .filter { it.key.startsWith("temperature_2m_member") }
        .mapNotNull { entry ->
            val arr = entry.value as? JsonArray ?: return@mapNotNull null
            arr.map { (it as? JsonPrimitive)?.doubleOrNull }
        }

    if (memberArrays.isEmpty()) return ConfidenceBandData(emptyList())

    val entries = times.mapIndexedNotNull { index, time ->
        val values = memberArrays.mapNotNull { it.getOrNull(index) }
        if (values.size < 3) return@mapIndexedNotNull null
        val sorted = values.sorted()
        val p10Index = ((sorted.size - 1) * 0.1).toInt()
        val p90Index = ((sorted.size - 1) * 0.9).toInt()
        ConfidenceBandEntry(
            time = time,
            temperatureMean = values.average(),
            temperatureLower = sorted[p10Index],
            temperatureUpper = sorted[p90Index],
        )
    }

    return ConfidenceBandData(entries)
}
