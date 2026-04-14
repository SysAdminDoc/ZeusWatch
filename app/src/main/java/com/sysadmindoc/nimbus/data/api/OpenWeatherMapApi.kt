package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.OwmOneCallResponse
import com.sysadmindoc.nimbus.data.model.OwmAirPollutionResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap One Call API 3.0.
 * Requires API key (free tier: 1,000 calls/day).
 * All responses in metric units when units=metric.
 */
interface OpenWeatherMapApi {

    @GET("onecall")
    suspend fun getOneCall(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("exclude") exclude: String = "minutely",
    ): OwmOneCallResponse

    @GET("air_pollution")
    suspend fun getAirPollution(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
    ): OwmAirPollutionResponse

    companion object {
        const val BASE_URL = "https://api.openweathermap.org/data/3.0/"
        const val AIR_POLLUTION_BASE_URL = "https://api.openweathermap.org/data/2.5/"
    }
}
