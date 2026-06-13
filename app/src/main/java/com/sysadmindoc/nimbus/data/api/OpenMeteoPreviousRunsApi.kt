package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Previous Runs API.
 *
 * Base URL: https://previous-runs-api.open-meteo.com/v1/
 * Returns the forecast as it was issued N days ago, using the same
 * response shape as the Forecast API.
 */
interface OpenMeteoPreviousRunsApi {

    @GET("forecast")
    suspend fun getPreviousRun(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("previous_day") previousDay: Int,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("precipitation_unit") precipitationUnit: String = "mm",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 10,
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://previous-runs-api.open-meteo.com/v1/"
        const val DAILY_PARAMS = "temperature_2m_max,temperature_2m_min,precipitation_sum"
    }
}
