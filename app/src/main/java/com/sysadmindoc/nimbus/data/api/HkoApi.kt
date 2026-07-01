package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Hong Kong Observatory Open Data API.
 * Keyless JSON endpoint for current weather, local forecast, 9-day forecast,
 * and active warning statements.
 */
interface HkoApi {

    @GET("weatherAPI/opendata/weather.php")
    suspend fun getForecast9Day(
        @Query("dataType") dataType: String = "fnd",
        @Query("lang") lang: String = "en",
    ): HkoForecastResponse

    @GET("weatherAPI/opendata/weather.php")
    suspend fun getCurrentReport(
        @Query("dataType") dataType: String = "rhrread",
        @Query("lang") lang: String = "en",
    ): HkoCurrentReportResponse

    @GET("weatherAPI/opendata/weather.php")
    suspend fun getLocalForecast(
        @Query("dataType") dataType: String = "flw",
        @Query("lang") lang: String = "en",
    ): HkoLocalForecastResponse

    @GET("weatherAPI/opendata/weather.php")
    suspend fun getWarningSummary(
        @Query("dataType") dataType: String = "warnsum",
        @Query("lang") lang: String = "en",
    ): Map<String, HkoWarningSummary>

    @GET("weatherAPI/opendata/weather.php")
    suspend fun getWarningInfo(
        @Query("dataType") dataType: String = "warningInfo",
        @Query("lang") lang: String = "en",
    ): HkoWarningInfoResponse

    companion object {
        const val BASE_URL = "https://data.weather.gov.hk/"
    }
}

@Serializable
data class HkoForecastResponse(
    val generalSituation: String? = null,
    val weatherForecast: List<HkoForecastDay> = emptyList(),
    val updateTime: String? = null,
)

@Serializable
data class HkoForecastDay(
    val forecastDate: String? = null,
    val week: String? = null,
    val forecastWind: String? = null,
    val forecastWeather: String? = null,
    val forecastMaxtemp: HkoValueUnit? = null,
    val forecastMintemp: HkoValueUnit? = null,
    val forecastMaxrh: HkoValueUnit? = null,
    val forecastMinrh: HkoValueUnit? = null,
    @SerialName("ForecastIcon") val forecastIcon: Int? = null,
    @SerialName("PSR") val probabilityOfSignificantRain: String? = null,
)

@Serializable
data class HkoCurrentReportResponse(
    val rainfall: HkoRainfallBlock? = null,
    val icon: List<Int> = emptyList(),
    val iconUpdateTime: String? = null,
    val uvindex: JsonElement? = null,
    val updateTime: String? = null,
    val temperature: HkoObservationBlock? = null,
    val warningMessage: JsonElement? = null,
    val tcmessage: List<String> = emptyList(),
    val humidity: HkoObservationBlock? = null,
)

@Serializable
data class HkoLocalForecastResponse(
    val generalSituation: String? = null,
    val tcInfo: String? = null,
    val fireDangerWarning: String? = null,
    val forecastPeriod: String? = null,
    val forecastDesc: String? = null,
    val outlook: String? = null,
    val updateTime: String? = null,
)

@Serializable
data class HkoRainfallBlock(
    val data: List<HkoRainfallEntry> = emptyList(),
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
data class HkoRainfallEntry(
    val unit: String? = null,
    val place: String? = null,
    val max: Double? = null,
    val min: Double? = null,
    val main: String? = null,
)

@Serializable
data class HkoObservationBlock(
    val data: List<HkoObservationEntry> = emptyList(),
    val recordTime: String? = null,
)

@Serializable
data class HkoObservationEntry(
    val place: String? = null,
    val value: Double? = null,
    val unit: String? = null,
)

@Serializable
data class HkoValueUnit(
    val value: Double? = null,
    val unit: String? = null,
)

@Serializable
data class HkoWarningSummary(
    val name: String? = null,
    val code: String? = null,
    val type: String? = null,
    val actionCode: String? = null,
    val issueTime: String? = null,
    val expireTime: String? = null,
    val updateTime: String? = null,
)

@Serializable
data class HkoWarningInfoResponse(
    val details: List<HkoWarningDetail> = emptyList(),
)

@Serializable
data class HkoWarningDetail(
    val contents: List<String> = emptyList(),
    val warningStatementCode: String? = null,
    val subtype: String? = null,
    val updateTime: String? = null,
)
