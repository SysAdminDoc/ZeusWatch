package com.sysadmindoc.nimbus.data.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Finnish Meteorological Institute WFS 2.0 Open Data service.
 *
 * Base URL: https://opendata.fmi.fi/
 */
interface FmiForecastApi {

    @GET("wfs")
    suspend fun getHarmonieForecast(
        @Query("service") service: String = "WFS",
        @Query("version") version: String = "2.0.0",
        @Query("request") request: String = "getFeature",
        @Query("storedquery_id") storedQueryId: String = HARMONIE_POINT_TIME_VALUE_QUERY,
        @Query("latlon") latLon: String,
        @Query("parameters") parameters: String = FORECAST_PARAMETERS,
        @Query("timestep") timestepMinutes: Int = 60,
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://opendata.fmi.fi/"
        const val HARMONIE_POINT_TIME_VALUE_QUERY =
            "fmi::forecast::harmonie::surface::point::timevaluepair"
        const val FORECAST_PARAMETERS =
            "Temperature,Humidity,Pressure,WindSpeedMS,WindDirection,WindGust," +
                "TotalCloudCover,Precipitation1h,Visibility,DewPoint,WeatherSymbol3"
    }
}
