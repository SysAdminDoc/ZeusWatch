package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoMarineApi {

    @GET("v1/marine")
    suspend fun getMarine(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
    ): MarineResponse

    companion object {
        const val BASE_URL = "https://marine-api.open-meteo.com/"
        const val CURRENT_PARAMS = "wave_height,wave_direction,wave_period,ocean_current_velocity,ocean_current_direction"
        const val HOURLY_PARAMS = "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_period,sea_surface_temperature"
    }
}

@Serializable
data class MarineResponse(
    val current: MarineCurrent? = null,
    val hourly: MarineHourly? = null,
)

@Serializable
data class MarineCurrent(
    @SerialName("wave_height") val waveHeight: Double? = null,
    @SerialName("wave_direction") val waveDirection: Int? = null,
    @SerialName("wave_period") val wavePeriod: Double? = null,
    @SerialName("ocean_current_velocity") val oceanCurrentVelocity: Double? = null,
    @SerialName("ocean_current_direction") val oceanCurrentDirection: Int? = null,
)

@Serializable
data class MarineHourly(
    @SerialName("wave_height") val waveHeight: List<Double?>? = null,
    @SerialName("wave_direction") val waveDirection: List<Int?>? = null,
    @SerialName("wave_period") val wavePeriod: List<Double?>? = null,
    @SerialName("swell_wave_height") val swellWaveHeight: List<Double?>? = null,
    @SerialName("swell_wave_period") val swellWavePeriod: List<Double?>? = null,
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: List<Double?>? = null,
)
