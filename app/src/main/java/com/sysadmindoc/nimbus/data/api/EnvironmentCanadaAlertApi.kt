package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Environment and Climate Change Canada (ECCC) weather alerts API.
 * Uses the CAP-CP (Common Alerting Protocol — Canadian Profile) endpoint.
 *
 * The official Atom/CAP feed lives at dd.weather.gc.ca, but for simpler
 * JSON consumption we target a proxied/adapted endpoint.
 * In production, consider parsing the raw CAP-CP XML directly.
 */
interface EnvironmentCanadaAlertApi {

    /**
     * Get active alerts for a province.
     * @param province Two-letter province code, e.g. "on", "bc", "ab".
     */
    @GET("rss/battleboard/{province}00_e.xml")
    suspend fun getProvinceAlerts(
        @Path("province") province: String,
    ): EnvironmentCanadaResponse

    companion object {
        const val BASE_URL = "https://weather.gc.ca/"
    }
}

@Serializable
data class EnvironmentCanadaResponse(
    val entries: List<EnvironmentCanadaEntry> = emptyList(),
)

@Serializable
data class EnvironmentCanadaEntry(
    val id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val updated: String? = null,
    val link: String? = null,
    @SerialName("cap:severity") val severity: String? = null,
    @SerialName("cap:urgency") val urgency: String? = null,
    @SerialName("cap:certainty") val certainty: String? = null,
    @SerialName("cap:areaDesc") val areaDesc: String? = null,
    @SerialName("cap:effective") val effective: String? = null,
    @SerialName("cap:expires") val expires: String? = null,
)
