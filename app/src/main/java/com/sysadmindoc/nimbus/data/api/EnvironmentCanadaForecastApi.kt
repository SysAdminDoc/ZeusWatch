package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Environment and Climate Change Canada (ECCC) forecast API.
 * Uses MSC GeoMet OGC API Features — `citypageweather-realtime`
 * collection on `api.weather.gc.ca`.
 *
 * No API key, Open Government Licence — Canada, JSON (GeoJSON) format.
 *
 * Schema documentation:
 *   https://eccc-msc.github.io/open-data/msc-data/citypageweather/readme_citypageweather_en/
 *
 * The endpoint accepts a `bbox` filter (min_lon,min_lat,max_lon,max_lat)
 * and returns a FeatureCollection of nearby city weather products. The
 * adapter picks the geographically closest feature. ECCC's free tier
 * does not publish hourly data in this collection — we populate
 * CurrentConditions + DailyConditions from the XML-derived forecastGroup.
 */
interface EnvironmentCanadaForecastApi {

    /**
     * Bounding-box query for nearby cities' weather products.
     *
     * @param bbox "<min_lon>,<min_lat>,<max_lon>,<max_lat>" per OGC spec.
     * @param limit number of features to return (default 10 gives us a
     *              handful of candidates to pick the closest from).
     */
    @GET("collections/citypageweather-realtime/items")
    suspend fun getCityWeather(
        @Query("bbox") bbox: String,
        @Query("lang") lang: String = "en",
        @Query("f") format: String = "json",
        @Query("limit") limit: Int = 10,
    ): EcccFeatureCollection

    companion object {
        const val BASE_URL = "https://api.weather.gc.ca/"
    }
}

@Serializable
data class EcccFeatureCollection(
    val type: String? = null,
    val features: List<EcccFeature> = emptyList(),
)

@Serializable
data class EcccFeature(
    val id: String? = null,
    val type: String? = null,
    val geometry: EcccGeometry? = null,
    val properties: EcccProperties? = null,
)

@Serializable
data class EcccGeometry(
    val type: String? = null,
    val coordinates: List<Double> = emptyList(),
)

@Serializable
data class EcccProperties(
    @SerialName("city_en") val cityEn: String? = null,
    @SerialName("city_fr") val cityFr: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("name_fr") val nameFr: String? = null,
    @SerialName("timestamp_utc") val timestampUtc: String? = null,
    val currentConditions: EcccCurrentConditions? = null,
    val forecastGroup: EcccForecastGroup? = null,
)

@Serializable
data class EcccCurrentConditions(
    val condition: String? = null,
    @SerialName("iconCode") val iconCode: JsonElement? = null,
    @SerialName("temperature_value") val temperatureValue: Double? = null,
    @SerialName("relativeHumidity_value") val relativeHumidityValue: Double? = null,
    @SerialName("pressure_value") val pressureValue: Double? = null, // kPa
    @SerialName("pressure_tendency") val pressureTendency: String? = null,
    @SerialName("dewpoint_value") val dewpointValue: Double? = null,
    @SerialName("visibility_value") val visibilityValue: Double? = null, // km
    @SerialName("wind_speed_value") val windSpeedValue: Double? = null, // km/h
    @SerialName("wind_bearing_value") val windBearingValue: Double? = null,
    @SerialName("wind_direction_value") val windDirectionValue: String? = null,
    @SerialName("wind_gust_value") val windGustValue: Double? = null,
    @SerialName("windChill_value") val windChillValue: Double? = null,
    @SerialName("humidex_value") val humidexValue: Double? = null,
    @SerialName("observationDateTimeUTC") val observationDateTimeUtc: String? = null,
)

@Serializable
data class EcccForecastGroup(
    val forecast: List<EcccForecastEntry> = emptyList(),
)

@Serializable
data class EcccForecastEntry(
    val period: String? = null,
    val textSummary: String? = null,
    val temperatures: List<EcccTemperature> = emptyList(),
    @SerialName("abbreviatedForecast") val abbreviatedForecast: EcccAbbreviatedForecast? = null,
)

@Serializable
data class EcccTemperature(
    @SerialName("class") val tempClass: String? = null,
    val value: Double? = null,
)

@Serializable
data class EcccAbbreviatedForecast(
    @SerialName("iconCode") val iconCode: JsonElement? = null,
    val textSummary: String? = null,
    @SerialName("pop_value") val pop: Double? = null, // probability of precipitation
)
