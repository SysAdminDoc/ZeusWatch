package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

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

    /**
     * SWIC's GeoServer, the same one its own map queries. Unlike the
     * country-wide JSON feed it keeps each alert area's polygon, so an
     * OGC `Intersects` filter answers "which warnings cover this point".
     */
    @GET(WFS_URL)
    suspend fun getFeatures(
        @Query("filter") filter: String,
        @Query("propertyName") propertyName: String = WFS_PROPERTIES,
        @Query("typeName") typeName: String = WFS_TYPE_NAME,
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("request") request: String = "GetFeature",
        @Query("version") version: String = "1.0.0",
        @Query("service") service: String = "WFS",
    ): WmoWfsResponse

    companion object {
        const val BASE_URL = "https://severeweather.wmo.int/v2/"
        const val WFS_URL = "https://severeweather.wmo.int/f/ows"
        const val WFS_TYPE_NAME = "local_postgis:postgis_geojsons"

        /** Leaves out `wkb_geometry`: the server already did the point test. */
        const val WFS_PROPERTIES =
            "capurl,event,headline,description,areadesc,sent,effective,onset,expires,mem,s,u,c,row_type"
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

@Serializable
data class WmoWfsResponse(
    val features: List<WmoWfsFeature> = emptyList(),
)

@Serializable
data class WmoWfsFeature(
    val properties: WmoWfsAlert = WmoWfsAlert(),
)

/**
 * One alert area row. `row_type` is `POLYGON` when the area has a shape and
 * `POINT` when the member published no geometry, in which case the row sits
 * on the member country's centroid and says nothing about the point.
 */
@Serializable
data class WmoWfsAlert(
    val capurl: String? = null,
    val event: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val areadesc: String? = null,
    val sent: String? = null,
    val effective: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    val mem: String? = null,
    val s: Int? = null,
    val u: Int? = null,
    val c: Int? = null,
    @SerialName("row_type") val rowType: String? = null,
)
