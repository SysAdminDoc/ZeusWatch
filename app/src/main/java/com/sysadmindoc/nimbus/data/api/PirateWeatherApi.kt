package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.PirateWeatherResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pirate Weather API (Dark Sky-compatible).
 * Requires API key (free tier: 20,000 calls/month).
 * Uses SI units by default.
 */
interface PirateWeatherApi {

    @GET("forecast/{apiKey}/{lat},{lon}")
    suspend fun getForecast(
        @Path("apiKey") apiKey: String,
        @Path("lat") latitude: Double,
        @Path("lon") longitude: Double,
        @Query("units") units: String = "si",
        @Query("exclude") exclude: String = "minutely",
    ): PirateWeatherResponse

    companion object {
        const val BASE_URL = "https://api.pirateweather.net/"
    }
}
