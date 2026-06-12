package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * WMO Severe Weather Information Centre (SWIC) CAP feed.
 * Aggregates active weather warnings from 130+ National Meteorological
 * and Hydrological Services worldwide via the Common Alerting Protocol.
 * Free, no key required.
 */
interface WmoAlertApi {

    @GET("json/wmo_all.json")
    suspend fun getWarnings(): WmoWarningsResponse

    @GET("json/wmo_member.json")
    suspend fun getMembers(): List<WmoMemberRegion>

    companion object {
        const val BASE_URL = "https://severeweather.wmo.int/v2/"
    }
}

@Serializable
data class WmoWarningsResponse(
    val itemCount: Int = 0,
    val lastUpdated: String? = null,
    val items: List<WmoWarning> = emptyList(),
)

@Serializable
data class WmoWarning(
    val id: String? = null,
    val event: String? = null,
    val headline: String? = null,
    val sent: String? = null,
    val expires: String? = null,
    val areaDesc: String? = null,
    val mid: String? = null,
    val ra: String? = null,
    val s: Int? = null,
    val u: Int? = null,
    val c: Int? = null,
    val capURL: String? = null,
    val effective: String? = null,
)

@Serializable
data class WmoMemberRegion(
    val ra: Int? = null,
    val members: List<WmoMember> = emptyList(),
)

@Serializable
data class WmoMember(
    val mid: String? = null,
    val name: String? = null,
    val dept: String? = null,
)
