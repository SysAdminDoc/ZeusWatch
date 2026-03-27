package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * EUMETNET MeteoAlarm API for European weather warnings.
 * Provides CAP-based alerts per country.
 * Docs: https://feeds.meteoalarm.org
 */
interface MeteoAlarmApi {

    @GET("api/v1/warnings/feeds-{country}")
    suspend fun getWarnings(
        @Path("country") countryCode: String, // ISO 3166-1 alpha-2 lowercase
    ): MeteoAlarmResponse

    companion object {
        const val BASE_URL = "https://feeds.meteoalarm.org/"
    }
}

@Serializable
data class MeteoAlarmResponse(
    val warnings: List<MeteoAlarmWarning> = emptyList(),
)

@Serializable
data class MeteoAlarmWarning(
    val identifier: String? = null,
    val sender: String? = null,
    val sent: String? = null,
    @SerialName("msg_type") val msgType: String? = null,
    val info: List<MeteoAlarmInfo> = emptyList(),
)

@Serializable
data class MeteoAlarmInfo(
    val event: String? = null,
    val severity: String? = null,        // Minor, Moderate, Severe, Extreme
    val urgency: String? = null,         // Immediate, Expected, Future, Past
    val certainty: String? = null,       // Observed, Likely, Possible, Unlikely
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    @SerialName("senderName") val senderName: String? = null,
    val area: List<MeteoAlarmArea> = emptyList(),
)

@Serializable
data class MeteoAlarmArea(
    @SerialName("areaDesc") val areaDesc: String? = null,
    val geocode: List<MeteoAlarmGeocode> = emptyList(),
)

@Serializable
data class MeteoAlarmGeocode(
    val valueName: String? = null,
    val value: String? = null,
)
