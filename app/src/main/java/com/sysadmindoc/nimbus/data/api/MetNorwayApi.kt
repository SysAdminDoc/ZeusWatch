package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.MetNorwayResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MET Norway LocationForecast 2.0 API.
 * Base URL: https://api.met.no/weatherapi/locationforecast/2.0/
 *
 * No API key required, global coverage (9 days), highest detail in the
 * Nordic region. **Must send a non-default User-Agent** — `okhttp`,
 * `Dalvik`, and `Java` are explicitly banned by MET's terms. ZeusWatch's
 * global `User-Agent: ZeusWatch/<ver> (Android; Open-Source)` interceptor
 * satisfies that requirement.
 *
 * The `/complete` endpoint returns JSON (GeoJSON Feature) with:
 *   - temperature percentiles (10th/90th) for uncertainty bands
 *   - probability_of_precipitation (1h + 6h + 12h)
 *   - probability_of_thunder
 *   - ultraviolet_index_clear_sky
 *   - fog_area_fraction
 *   - cloud layer fractions (high/medium/low)
 *   - wind_speed_of_gust
 *
 * License: CC BY 4.0 (requires attribution in UI/README).
 */
interface MetNorwayApi {

    @GET("complete")
    suspend fun getForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("altitude") altitude: Int? = null,
    ): MetNorwayResponse

    companion object {
        const val BASE_URL = "https://api.met.no/weatherapi/locationforecast/2.0/"
    }
}
