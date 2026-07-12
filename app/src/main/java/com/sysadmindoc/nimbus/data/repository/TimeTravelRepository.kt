package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenMeteoArchiveApi
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.TimeTravelDay
import com.sysadmindoc.nimbus.data.model.WeatherCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Which data source can answer a time-travel query for a given date. */
enum class TimeTravelSource { ARCHIVE, FORECAST, OUT_OF_RANGE }

/**
 * Pure date-range routing for the time-travel scrubber. Kept side-effect free so
 * it is exhaustively unit-testable without a network or Android dependency.
 */
object TimeTravelRange {
    /** Open-Meteo's archive reanalysis reaches back to 1940. */
    val MIN_DATE: LocalDate = LocalDate.of(1940, 1, 1)

    /** The loaded forecast covers today plus the next 15 days (16-day forecast). */
    const val FORECAST_HORIZON_DAYS = 15L

    fun classify(date: LocalDate, today: LocalDate): TimeTravelSource = when {
        date.isBefore(MIN_DATE) -> TimeTravelSource.OUT_OF_RANGE
        date.isAfter(today.plusDays(FORECAST_HORIZON_DAYS)) -> TimeTravelSource.OUT_OF_RANGE
        date.isBefore(today) -> TimeTravelSource.ARCHIVE
        else -> TimeTravelSource.FORECAST
    }

    fun maxSelectableDate(today: LocalDate): LocalDate = today.plusDays(FORECAST_HORIZON_DAYS)
}

/**
 * Answers "what was / will the weather be on an arbitrary date at this location".
 * Past dates resolve against the Open-Meteo archive; today and near-future dates
 * are read from the already-loaded forecast so no extra network call is made.
 */
@Singleton
class TimeTravelRepository @Inject constructor(
    private val archiveApi: OpenMeteoArchiveApi,
) {

    /**
     * @param forecastDaily the currently loaded 16-day forecast, used to answer
     *   today/future dates offline. Past dates ignore it and hit the archive.
     * @return the day's weather, or `null` when out of range, absent from the
     *   forecast window, or the archive lookup fails (e.g. the ~2-day archive lag
     *   for very recent dates, or a network error).
     */
    suspend fun getDay(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        forecastDaily: List<DailyConditions>,
        today: LocalDate = LocalDate.now(),
    ): TimeTravelDay? = when (TimeTravelRange.classify(date, today)) {
        TimeTravelSource.OUT_OF_RANGE -> null
        TimeTravelSource.FORECAST -> forecastDaily.firstOrNull { it.date == date }?.let { d ->
            TimeTravelDay(
                date = date,
                weatherCode = d.weatherCode,
                highC = d.temperatureHigh,
                lowC = d.temperatureLow,
                precipMm = d.precipitationSum,
                isHistorical = false,
            )
        }
        TimeTravelSource.ARCHIVE -> fetchArchiveDay(latitude, longitude, date)
    }

    private suspend fun fetchArchiveDay(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
    ): TimeTravelDay? = withContext(Dispatchers.IO) {
        val iso = DateTimeFormatter.ISO_LOCAL_DATE.format(date)
        runCatching {
            val daily = archiveApi.getArchive(
                latitude = latitude,
                longitude = longitude,
                startDate = iso,
                endDate = iso,
            ).daily ?: return@runCatching null
            val index = daily.time.indexOf(iso).takeIf { it >= 0 } ?: return@runCatching null
            val high = daily.temperature_2m_max?.getOrNull(index) ?: return@runCatching null
            val low = daily.temperature_2m_min?.getOrNull(index) ?: return@runCatching null
            TimeTravelDay(
                date = date,
                weatherCode = WeatherCode.fromCode(daily.weather_code?.getOrNull(index)),
                highC = high,
                lowC = low,
                precipMm = daily.precipitation_sum?.getOrNull(index),
                isHistorical = true,
            )
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }
}
