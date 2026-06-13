package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoFloodApi {

    @GET("v1/flood")
    suspend fun getFlood(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("forecast_days") forecastDays: Int = 30,
    ): FloodResponse

    companion object {
        const val BASE_URL = "https://flood-api.open-meteo.com/"
        const val DAILY_PARAMS = "river_discharge,river_discharge_mean,river_discharge_max,river_discharge_min"
    }
}

@Serializable
data class FloodResponse(
    val daily: FloodDaily? = null,
)

@Serializable
data class FloodDaily(
    val time: List<String>? = null,
    @SerialName("river_discharge") val riverDischarge: List<Double?>? = null,
    @SerialName("river_discharge_mean") val riverDischargeMean: List<Double?>? = null,
    @SerialName("river_discharge_max") val riverDischargeMax: List<Double?>? = null,
    @SerialName("river_discharge_min") val riverDischargeMin: List<Double?>? = null,
)
