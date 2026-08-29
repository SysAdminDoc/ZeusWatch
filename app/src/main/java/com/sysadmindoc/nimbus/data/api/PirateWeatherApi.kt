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
        @Query("exclude") exclude: String = FORECAST_EXCLUDE,
    ): PirateWeatherResponse

    /**
     * Air quality, requested separately on US units.
     *
     * Pirate Weather derives the index from the requested unit system: `si`
     * yields the EU CAQI and `ca` the Canadian AQHI, so the forecast call's
     * `si` cannot also serve US EPA AQI. Everything but `currently` is
     * excluded because only the current index is consumed.
     */
    @GET("forecast/{apiKey}/{lat},{lon}")
    suspend fun getAirQuality(
        @Path("apiKey") apiKey: String,
        @Path("lat") latitude: Double,
        @Path("lon") longitude: Double,
        @Query("units") units: String = "us",
        @Query("exclude") exclude: String = AIR_QUALITY_EXCLUDE,
        @Query("include") include: String = "airqualitydetails",
    ): PirateWeatherResponse

    companion object {
        const val BASE_URL = "https://api.pirateweather.net/"

        /**
         * The adapter reads `currently`, `hourly` and `daily`. Nothing consumes
         * Pirate Weather alerts (NWS is the US alert source), so excluding
         * them and the minutely block trims the reply the app never looks at.
         */
        const val FORECAST_EXCLUDE = "minutely,alerts"

        const val AIR_QUALITY_EXCLUDE = "minutely,hourly,daily,alerts"
    }
}
