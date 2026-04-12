package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Archive API — historical weather observations.
 *
 * Base URL: https://archive-api.open-meteo.com/v1/
 * No API key. The archive is updated on a ~2-day lag (today minus 2 is
 * typically the latest available date).
 */
interface OpenMeteoArchiveApi {

    @GET("archive")
    suspend fun getArchive(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String, // "yyyy-MM-dd"
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code",
        @Query("timezone") timezone: String = "auto",
    ): ArchiveResponse

    companion object {
        const val BASE_URL = "https://archive-api.open-meteo.com/v1/"
    }
}

@Serializable
data class ArchiveResponse(
    val daily: ArchiveDaily? = null,
)

@Serializable
data class ArchiveDaily(
    val time: List<String> = emptyList(),
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
    val weather_code: List<Int?>? = null,
)
