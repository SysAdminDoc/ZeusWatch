package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.BrightSkyWeatherResponse
import com.sysadmindoc.nimbus.data.model.BrightSkyAlertResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Bright Sky API — free, open-source proxy for DWD (German Weather Service) data.
 * No API key required. Best coverage in/near Germany.
 * Docs: https://brightsky.dev/docs/
 */
interface BrightSkyApi {

    @GET("weather")
    suspend fun getWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("date") date: String, // YYYY-MM-DD
        @Query("last_date") lastDate: String, // YYYY-MM-DD (exclusive)
        @Query("tz") timezone: String = "Etc/UTC",
    ): BrightSkyWeatherResponse

    @GET("current_weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): BrightSkyWeatherResponse

    @GET("alerts")
    suspend fun getAlerts(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): BrightSkyAlertResponse

    companion object {
        const val BASE_URL = "https://api.brightsky.dev/"
    }
}
