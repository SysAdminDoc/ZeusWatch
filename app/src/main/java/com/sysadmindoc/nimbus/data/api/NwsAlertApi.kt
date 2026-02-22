package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * National Weather Service Alerts API.
 * Public domain, no API key, unlimited.
 * Docs: https://www.weather.gov/documentation/services-web-api
 */
interface NwsAlertApi {

    @GET("alerts/active")
    suspend fun getActiveAlerts(
        @Query("point") point: String, // "lat,lon"
        @Query("status") status: String = "actual",
        @Query("message_type") messageType: String = "alert,update",
    ): NwsAlertResponse

    companion object {
        const val BASE_URL = "https://api.weather.gov/"
    }
}

@Serializable
data class NwsAlertResponse(
    val type: String? = null,
    val features: List<NwsAlertFeature> = emptyList(),
    val title: String? = null,
    val updated: String? = null,
)

@Serializable
data class NwsAlertFeature(
    val id: String? = null,
    val type: String? = null,
    val properties: NwsAlertProperties? = null,
)

@Serializable
data class NwsAlertProperties(
    @SerialName("@id") val alertId: String? = null,
    val areaDesc: String? = null,
    val sent: String? = null,
    val effective: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    val ends: String? = null,
    val status: String? = null,
    val messageType: String? = null,
    val category: String? = null,
    val severity: String? = null,       // Extreme, Severe, Moderate, Minor, Unknown
    val certainty: String? = null,      // Observed, Likely, Possible, Unlikely, Unknown
    val urgency: String? = null,        // Immediate, Expected, Future, Past, Unknown
    val event: String? = null,          // "Tornado Warning", "Winter Storm Watch" etc
    val sender: String? = null,
    val senderName: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val response: String? = null,       // Shelter, Evacuate, Prepare, Execute, Avoid, Monitor, AllClear, None
)
