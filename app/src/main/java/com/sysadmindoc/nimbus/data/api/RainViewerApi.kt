package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * RainViewer Weather Maps API.
 * Returns timestamped radar tile paths for past 2hr + 30min forecast.
 * Tiles: {host}/{path}/512/{z}/{x}/{y}/{color}/{options}.png
 * Docs: https://www.rainviewer.com/api/weather-maps-api.html
 */
interface RainViewerApi {

    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse

    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
        const val TILE_HOST = "https://tilecache.rainviewer.com"

        /** Build a tile URL template for MapLibre RasterSource.
         *  {z}/{x}/{y} placeholders are required by MapLibre. */
        fun buildTileUrl(
            path: String,
            colorScheme: Int = 4, // 4 = "The Weather Channel" scheme
            smooth: Boolean = true,
            snow: Boolean = true,
        ): String {
            val smoothFlag = if (smooth) 1 else 0
            val snowFlag = if (snow) 1 else 0
            return "$TILE_HOST$path/512/{z}/{x}/{y}/$colorScheme/${smoothFlag}_${snowFlag}.png"
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
