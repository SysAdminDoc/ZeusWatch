package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Air Quality API.
 * Base URL: https://air-quality-api.open-meteo.com/v1/
 * No API key. Includes pollutants + European/US AQI + pollen.
 */
interface AirQualityApi {

    @GET("air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 3,
    ): AirQualityResponse

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/"

        const val CURRENT_PARAMS = "us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide," +
            "nitrogen_dioxide,sulphur_dioxide,ozone,dust,uv_index," +
            "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"

        const val HOURLY_PARAMS = "us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide," +
            "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
    }
}

@Serializable
data class AirQualityResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val current: AqCurrent? = null,
    val hourly: AqHourly? = null,
)

@Serializable
data class AqCurrent(
    val time: String? = null,
    @SerialName("us_aqi") val usAqi: Int? = null,
    @SerialName("european_aqi") val europeanAqi: Int? = null,
    val pm10: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    @SerialName("carbon_monoxide") val carbonMonoxide: Double? = null,
    @SerialName("nitrogen_dioxide") val nitrogenDioxide: Double? = null,
    @SerialName("sulphur_dioxide") val sulphurDioxide: Double? = null,
    val ozone: Double? = null,
    val dust: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    @SerialName("alder_pollen") val alderPollen: Double? = null,
    @SerialName("birch_pollen") val birchPollen: Double? = null,
    @SerialName("grass_pollen") val grassPollen: Double? = null,
    @SerialName("mugwort_pollen") val mugwortPollen: Double? = null,
    @SerialName("olive_pollen") val olivePollen: Double? = null,
    @SerialName("ragweed_pollen") val ragweedPollen: Double? = null,
)

@Serializable
data class AqHourly(
    val time: List<String> = emptyList(),
    @SerialName("us_aqi") val usAqi: List<Int?>? = null,
    @SerialName("pm2_5") val pm25: List<Double?>? = null,
    val pm10: List<Double?>? = null,
    val ozone: List<Double?>? = null,
    @SerialName("nitrogen_dioxide") val nitrogenDioxide: List<Double?>? = null,
    @SerialName("alder_pollen") val alderPollen: List<Double?>? = null,
    @SerialName("birch_pollen") val birchPollen: List<Double?>? = null,
    @SerialName("grass_pollen") val grassPollen: List<Double?>? = null,
    @SerialName("mugwort_pollen") val mugwortPollen: List<Double?>? = null,
    @SerialName("olive_pollen") val olivePollen: List<Double?>? = null,
    @SerialName("ragweed_pollen") val ragweedPollen: List<Double?>? = null,
)
