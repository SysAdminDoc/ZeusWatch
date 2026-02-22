package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Geocoding API for location name resolution.
 * Base URL: https://geocoding-api.open-meteo.com/v1/
 */
interface GeocodingApi {

    @GET("search")
    suspend fun searchLocation(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponse

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponse

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/v1/"
    }
}

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null,
    @SerialName("generationtime_ms") val generationTimeMs: Double? = null,
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    @SerialName("feature_code") val featureCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    @SerialName("admin1") val admin1: String? = null, // State/Region
    @SerialName("admin2") val admin2: String? = null, // County
    @SerialName("admin3") val admin3: String? = null,
    val timezone: String? = null,
    val population: Int? = null,
)
