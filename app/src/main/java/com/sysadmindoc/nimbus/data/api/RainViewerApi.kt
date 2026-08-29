package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * RainViewer Weather Maps API.
 * Returns timestamped radar tile paths for the most recent past radar frames.
 * Tiles: {host}/{path}/512/{z}/{x}/{y}/{color}/{options}.png
 * Docs: https://www.rainviewer.com/api/weather-maps-api.html
 */
interface RainViewerApi {

    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse

    /**
     * CAP alert polygons for a viewport, served only by LibreWXR.
     *
     * Most of the app's alert sources (MeteoAlarm, JMA, HKO, WMO) publish text
     * without geometry, so their alerts never reach the radar map. LibreWXR
     * carries the WMO CAP polygons, which fills that gap for those regions.
     *
     * `bbox` is `minLon,minLat,maxLon,maxLat` and `simplify` is a degree
     * tolerance; both were confirmed against the live service.
     */
    @GET("v2/alerts")
    suspend fun getCapAlerts(
        @Query("bbox") bbox: String,
        @Query("simplify") simplify: Double = DEFAULT_ALERT_SIMPLIFY_DEGREES,
    ): CapAlertCollection

    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
        const val LIBREWXR_BASE_URL = "https://api.librewxr.net/"
        const val TILE_HOST = "https://tilecache.rainviewer.com"
        const val UNIVERSAL_BLUE_COLOR_SCHEME = 2
        const val LIBREWXR_VIPER_HD_COLOR_SCHEME = 10
        const val MAX_TILE_ZOOM = 7
        const val LIBREWXR_MAX_TILE_ZOOM = 12
        const val PUBLIC_TILE_FORMAT = "png"

        /**
         * Roughly a kilometre of tolerance. Alert polygons are advisory
         * boundaries drawn around administrative areas, so shaving vertices
         * saves a lot of payload without moving any edge a user could act on.
         */
        const val DEFAULT_ALERT_SIMPLIFY_DEGREES = 0.01
        const val SUPPORTS_PUBLIC_NOWCAST_TILES = false
        const val SUPPORTS_PUBLIC_SATELLITE_TILES = false

        /**
         * Build a RainViewer radar tile URL template for MapLibre RasterSource.
         *
         * RainViewer's public API keeps only Universal Blue radar tiles as of
         * January 2026, so unsupported scheme IDs are forced back to that value.
         *  {z}/{x}/{y} placeholders are required by MapLibre. */
        fun buildTileUrl(
            path: String,
            host: String? = null,
            colorScheme: Int = UNIVERSAL_BLUE_COLOR_SCHEME,
            smooth: Boolean = true,
            snow: Boolean = true,
            restrictToPublicRainViewer: Boolean = true,
        ): String {
            val resolvedHost = host?.takeIf { it.isNotBlank() } ?: TILE_HOST
            val supportedColorScheme = if (restrictToPublicRainViewer && colorScheme != UNIVERSAL_BLUE_COLOR_SCHEME) {
                UNIVERSAL_BLUE_COLOR_SCHEME
            } else {
                colorScheme
            }
            val smoothFlag = if (smooth) 1 else 0
            val snowFlag = if (snow) 1 else 0
            return "$resolvedHost$path/512/{z}/{x}/{y}/$supportedColorScheme/${smoothFlag}_${snowFlag}.png"
        }
    }
}

@Serializable
data class RainViewerResponse(
    val version: String? = null,
    val generated: Long? = null,
    val host: String? = null,
    val radar: RadarData? = null,
    val satellite: SatelliteData? = null,
)

@Serializable
data class RadarData(
    val past: List<RadarFrame> = emptyList(),
    // Legacy field kept nullable-compatible for old examples/proxies. The
    // public RainViewer API discontinued future nowcast frames in January 2026.
    val nowcast: List<RadarFrame> = emptyList(),
)

@Serializable
data class RadarFrame(
    val time: Long, // Unix timestamp
    val path: String, // Tile path prefix
)

@Serializable
data class SatelliteData(
    val infrared: List<RadarFrame>? = null,
)

/** GeoJSON FeatureCollection of CAP alerts, as served by LibreWXR `/v2/alerts`. */
@Serializable
data class CapAlertCollection(
    val type: String = "FeatureCollection",
    val features: List<CapAlertFeature> = emptyList(),
)

@Serializable
data class CapAlertFeature(
    val geometry: CapAlertGeometry? = null,
    val properties: CapAlertProperties = CapAlertProperties(),
)

/**
 * Coordinates arrive as raw JSON because the endpoint mixes `Polygon` (one
 * ring list) and `MultiPolygon` (a list of those) in the same collection.
 */
@Serializable
data class CapAlertGeometry(
    val type: String = "",
    val coordinates: JsonElement? = null,
)

@Serializable
data class CapAlertProperties(
    val title: String = "",
    val severity: String = "",
    val time: Long = 0L,
    val expires: Long = 0L,
    val description: String = "",
    val regions: String = "",
    /** The CAP alert URI, stable enough to dedupe against the app's own alerts. */
    val uri: String = "",
)
