package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Forecast API.
 * Base URL: https://api.open-meteo.com/v1/
 * No API key required. 10,000 calls/day free.
 */
interface OpenMeteoApi {

    @GET("forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("precipitation_unit") precipitationUnit: String = "mm",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16,
        @Query("forecast_hours") forecastHours: Int = 48,
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/v1/"

        const val CURRENT_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
            "is_day,precipitation,weather_code,cloud_cover,pressure_msl,surface_pressure," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,visibility,dew_point_2m"

        const val HOURLY_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
            "precipitation_probability,precipitation,weather_code,cloud_cover,visibility," +
            "wind_speed_10m,wind_direction_10m,uv_index,is_day"

        const val DAILY_PARAMS = "weather_code,temperature_2m_max,temperature_2m_min," +
            "apparent_temperature_max,apparent_temperature_min,sunrise,sunset,uv_index_max," +
            "precipitation_sum,precipitation_probability_max,wind_speed_10m_max," +
            "wind_direction_10m_dominant,precipitation_hours"
    }
}
