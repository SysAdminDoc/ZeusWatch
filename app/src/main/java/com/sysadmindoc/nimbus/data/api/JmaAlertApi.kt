package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Japan Meteorological Agency (JMA) alerts API.
 * Uses the JMA XML/JSON feed for extra weather warnings.
 * Docs: https://www.data.jma.go.jp/developer/
 *
 * Note: JMA's actual XML feed is complex; this interface targets a simplified
 * JSON proxy/adapter. In production, you may want to use an XML parser or
 * a third-party proxy that converts JMA XML to JSON.
 */
interface JmaAlertApi {

    @GET("developer/xml/feed/extra_json.json")
    suspend fun getAlerts(): JmaAlertResponse

    companion object {
        const val BASE_URL = "https://www.data.jma.go.jp/"
    }
}

@Serializable
data class JmaAlertResponse(
    val entries: List<JmaAlertEntry> = emptyList(),
)

@Serializable
data class JmaAlertEntry(
    val id: String? = null,
    val title: String? = null,
    val updated: String? = null,
    val content: String? = null,
    val link: String? = null,
    val author: String? = null,
    // CAP-like fields when available
    val severity: String? = null,
    val urgency: String? = null,
    val certainty: String? = null,
    val area: String? = null,
    val onset: String? = null,
    val expires: String? = null,
)
