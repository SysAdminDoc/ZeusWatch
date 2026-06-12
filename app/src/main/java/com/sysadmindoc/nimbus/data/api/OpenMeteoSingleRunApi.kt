package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Single Runs API.
 *
 * Base URL: https://single-runs-api.open-meteo.com/v1/
 * Same response shape as the Forecast API, plus a required UTC model-run time.
 */
interface OpenMeteoSingleRunApi {

    @GET("forecast")
    suspend fun getForecastRun(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("run") run: String,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_hours") forecastHours: Int = 36,
        @Query("models") models: String = MODEL_ICON_GLOBAL,
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://single-runs-api.open-meteo.com/v1/"
        const val HOURLY_PARAMS = "temperature_2m,precipitation_probability"
        const val MODEL_ICON_GLOBAL = "icon_global"
    }
}
