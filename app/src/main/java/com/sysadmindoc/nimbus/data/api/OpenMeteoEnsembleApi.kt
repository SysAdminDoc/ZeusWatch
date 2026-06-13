package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.EnsembleResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoEnsembleApi {

    @GET("ensemble")
    suspend fun getEnsemble(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("models") models: String = DEFAULT_MODEL,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 3,
    ): EnsembleResponse

    companion object {
        const val BASE_URL = "https://ensemble-api.open-meteo.com/v1/"
        const val HOURLY_PARAMS = "temperature_2m"
        const val DEFAULT_MODEL = "icon_seamless"
    }
}
