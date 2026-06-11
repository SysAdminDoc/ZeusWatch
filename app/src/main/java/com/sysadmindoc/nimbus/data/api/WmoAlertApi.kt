package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * WMO Severe Weather Information Centre (SWIC) CAP feed.
 * Aggregates active weather warnings from 130+ National Meteorological
 * and Hydrological Services worldwide via the Common Alerting Protocol.
 * Free, no key required.
 */
interface WmoAlertApi {

    @GET("json/warnings.json")
    suspend fun getWarnings(): WmoWarningsResponse

    companion object {
        const val BASE_URL = "https://severeweather.wmo.int/v2/"
    }
}

@Serializable
data class WmoWarningsResponse(
    val warnings: List<WmoWarning> = emptyList(),
)

@Serializable
data class WmoWarning(
    val id: String? = null,
    val capId: String? = null,
    val event: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val severity: String? = null,
    val urgency: String? = null,
    val certainty: String? = null,
    val sender: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    val effective: String? = null,
    val expires: String? = null,
    val onset: String? = null,
    @SerialName("area_desc") val areaDesc: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("min_lat") val minLat: Double? = null,
    @SerialName("max_lat") val maxLat: Double? = null,
    @SerialName("min_lon") val minLon: Double? = null,
    @SerialName("max_lon") val maxLon: Double? = null,
    val status: String? = null,
    @SerialName("msg_type") val msgType: String? = null,
)
