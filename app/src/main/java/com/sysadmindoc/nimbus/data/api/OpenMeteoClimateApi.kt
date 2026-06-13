package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoClimateApi {

    @GET("v1/climate")
    suspend fun getClimate(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("models") models: String = DEFAULT_MODEL,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
    ): ClimateResponse

    companion object {
        const val BASE_URL = "https://climate-api.open-meteo.com/"
        const val DAILY_PARAMS = "temperature_2m_max,temperature_2m_min,precipitation_sum"
        const val DEFAULT_MODEL = "EC_Earth3P_HR"
    }
}

@Serializable
data class ClimateResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val daily: ClimateDaily? = null,
)

@Serializable
data class ClimateDaily(
    val time: List<String>? = null,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double?>? = null,
)
