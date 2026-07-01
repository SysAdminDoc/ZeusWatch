package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * RainViewer Weather Maps API.
 * Returns timestamped radar tile paths for the most recent past radar frames.
 * Tiles: {host}/{path}/512/{z}/{x}/{y}/{color}/{options}.png
 * Docs: https://www.rainviewer.com/api/weather-maps-api.html
 */
interface RainViewerApi {

    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse

    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
        const val TILE_HOST = "https://tilecache.rainviewer.com"
        const val UNIVERSAL_BLUE_COLOR_SCHEME = 2
        const val MAX_TILE_ZOOM = 7
        const val PUBLIC_TILE_FORMAT = "png"
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
        ): String {
            val resolvedHost = host?.takeIf { it.isNotBlank() } ?: TILE_HOST
            val supportedColorScheme = if (colorScheme == UNIVERSAL_BLUE_COLOR_SCHEME) {
                colorScheme
            } else {
                UNIVERSAL_BLUE_COLOR_SCHEME
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
