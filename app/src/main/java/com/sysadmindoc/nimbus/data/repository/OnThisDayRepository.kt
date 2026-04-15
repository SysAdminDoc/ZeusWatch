package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import com.sysadmindoc.nimbus.data.api.OpenMeteoArchiveApi
import com.sysadmindoc.nimbus.data.model.OnThisDayData
import com.sysadmindoc.nimbus.data.model.PriorYearEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches "on this day, in prior years" historical weather from Open-Meteo's
 * archive endpoint.
 *
 * Historical observations for a given (location, calendar date) do not change
 * once logged, so results are cached permanently in SharedPreferences keyed by
 * `lat,lon,MM-dd`. First access per location+date costs ~10 parallel-able
 * archive requests; subsequent accesses are instant and offline-safe.
 */
@Singleton
class OnThisDayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiveApi: OpenMeteoArchiveApi,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Get "on this day" data for [lat]/[lon] on the calendar date of [today].
     * Returns `null` if the archive has no usable observations (e.g. polar
     * regions, brand-new settlements, or network failure with empty cache).
     */
    suspend fun getOnThisDay(
        lat: Double,
        lon: Double,
        today: LocalDate = LocalDate.now(),
    ): OnThisDayData? = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(lat, lon, today)
        readCache(cacheKey)?.let { return@withContext it }

        val entries = fetchPriorYears(lat, lon, today)
        if (entries.isEmpty()) return@withContext null

        val data = buildData(entries)
        writeCache(cacheKey, data)
        data
    }

    private suspend fun fetchPriorYears(
        lat: Double,
        lon: Double,
        today: LocalDate,
    ): List<PriorYearEntry> {
        // Archive has a ~2-day lag; start from last year to be safe.
        val endYear = today.year - 1
        val startYear = endYear - HISTORY_SPAN_YEARS + 1
        // Clamp day for Feb 29 in non-leap years to avoid DateTimeException
        val startDate = safeDate(startYear, today.monthValue, today.dayOfMonth)
        val endDate = safeDate(endYear, today.monthValue, today.dayOfMonth)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        val response = runCatching {
            archiveApi.getArchive(
                latitude = lat,
                longitude = lon,
                startDate = fmt.format(startDate),
                endDate = fmt.format(endDate),
            )
        }.getOrNull() ?: return emptyList()

        val daily = response.daily ?: return emptyList()
        val targetMonthDay = "%02d-%02d".format(
            Locale.US, today.monthValue, today.dayOfMonth,
        )
        val out = mutableListOf<PriorYearEntry>()
        for (i in daily.time.indices) {
            val dateStr = daily.time[i]
            // Only keep rows that exactly match today's month+day across years.
            // Leap-day requests will only find a row every ~4 years — acceptable.
            if (!dateStr.endsWith(targetMonthDay)) continue
            val year = dateStr.substring(0, 4).toIntOrNull() ?: continue
            val high = daily.temperature_2m_max?.getOrNull(i) ?: continue
            val low = daily.temperature_2m_min?.getOrNull(i) ?: continue
            val precip = daily.precipitation_sum?.getOrNull(i)
            out += PriorYearEntry(year = year, highC = high, lowC = low, precipMm = precip)
        }
        // Newest first for display
        return out.sortedByDescending { it.year }
    }

    private fun buildData(entries: List<PriorYearEntry>): OnThisDayData {
        val highs = entries.map { it.highC }
        val lows = entries.map { it.lowC }
        return OnThisDayData(
            priorYears = entries,
            averageHighC = highs.average(),
            averageLowC = lows.average(),
            recordHighC = highs.max(),
            recordLowC = lows.min(),
        )
    }

    private fun cacheKey(lat: Double, lon: Double, date: LocalDate): String =
        String.format(
            Locale.US,
            "%.2f,%.2f,%02d-%02d",
            lat, lon, date.monthValue, date.dayOfMonth,
        )

    private fun readCache(key: String): OnThisDayData? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val cached = json.decodeFromString(CachedData.serializer(), raw)
            OnThisDayData(
                priorYears = cached.priorYears.map {
                    PriorYearEntry(it.year, it.highC, it.lowC, it.precipMm)
                },
                averageHighC = cached.averageHighC,
                averageLowC = cached.averageLowC,
                recordHighC = cached.recordHighC,
                recordLowC = cached.recordLowC,
            )
        }.getOrNull()
    }

    private fun writeCache(key: String, data: OnThisDayData) {
        val payload = CachedData(
            priorYears = data.priorYears.map {
                CachedPriorYear(it.year, it.highC, it.lowC, it.precipMm)
            },
            averageHighC = data.averageHighC,
            averageLowC = data.averageLowC,
            recordHighC = data.recordHighC,
            recordLowC = data.recordLowC,
        )
        prefs.edit()
            .putString(key, json.encodeToString(CachedData.serializer(), payload))
            .apply()
    }

    @Serializable
    private data class CachedData(
        val priorYears: List<CachedPriorYear>,
        val averageHighC: Double,
        val averageLowC: Double,
        val recordHighC: Double,
        val recordLowC: Double,
    )

    @Serializable
    private data class CachedPriorYear(
        val year: Int,
        val highC: Double,
        val lowC: Double,
        val precipMm: Double?,
    )

    /** Clamp day of month for years where it doesn't exist (e.g. Feb 29 in non-leap years). */
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, maxDay))
    }

    companion object {
        private const val PREFS_NAME = "nimbus_on_this_day"
        private const val HISTORY_SPAN_YEARS = 10
    }
}
