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
     *
     * `version=2` is required, not optional: `airQualityIndex` is one of the
     * fields the docs list as present only when `version>1`. Without it the
     * reply parses fine and the index is simply absent, so the mistake looks
     * like "this location has no air quality data".
     */
    @GET("forecast/{apiKey}/{lat},{lon}")
    suspend fun getAirQuality(
        @Path("apiKey") apiKey: String,
        @Path("lat") latitude: Double,
        @Path("lon") longitude: Double,
        @Query("units") units: String = "us",
        @Query("version") version: Int = AIR_QUALITY_API_VERSION,
        @Query("exclude") exclude: String = AIR_QUALITY_EXCLUDE,
        @Query("include") include: String = "airqualitydetails",
    ): PirateWeatherResponse

    companion object {
        const val BASE_URL = "https://api.pirateweather.net/"

        /**
         * The forecast adapter reads `currently`, `hourly` and `daily` only.
         *
         * Alerts are excluded here because this endpoint feeds the forecast
         * adapter; `PirateWeatherAlertAdapter` is a separate caller that
         * passes its own `exclude` and is unaffected.
         */
        const val FORECAST_EXCLUDE = "minutely,alerts"

        const val AIR_QUALITY_EXCLUDE = "minutely,hourly,daily,alerts"

        /** airQualityIndex is a version>1 field. */
        const val AIR_QUALITY_API_VERSION = 2
    }
}
