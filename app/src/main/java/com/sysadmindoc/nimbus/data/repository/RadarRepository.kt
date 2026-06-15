package com.sysadmindoc.nimbus.data.repository

import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.api.RadarFrame
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

@Singleton
class RadarRepository @Inject constructor(
    private val rainViewerApi: RainViewerApi,
) {
    fun buildPreviewTileUrls(
        lat: Double,
        lon: Double,
        tileUrlTemplate: String,
        zoom: Int = PREVIEW_ZOOM,
    ): RadarPreviewUrls {
        val (tileX, tileY) = latLonToTile(lat, lon, zoom)
        val radarUrl = tileUrlTemplate
            .replace("{z}", zoom.toString())
            .replace("{x}", tileX.toString())
            .replace("{y}", tileY.toString())
        val baseMapUrl = "https://basemaps.cartocdn.com/dark_all/$zoom/$tileX/$tileY@2x.png"
        return RadarPreviewUrls(radarUrl, baseMapUrl)
    }

    suspend fun getRadarFrames(): Result<RadarFrameSet> = withContext(Dispatchers.IO) {
        try {
            val response = rainViewerApi.getWeatherMaps()
            val radar = response.radar ?: return@withContext Result.failure(
                Exception("No radar data available")
            )
            val host = response.host
            val past = radar.past.map { frame ->
                TimedTileUrl(
                    timestamp = frame.time,
                    tileUrl = RainViewerApi.buildTileUrl(frame.path, host),
                    isPast = true,
                )
            }
            val forecast = radar.nowcast.map { frame ->
                TimedTileUrl(
                    timestamp = frame.time,
                    tileUrl = RainViewerApi.buildTileUrl(frame.path, host),
                    isPast = false,
                )
            }
            Result.success(RadarFrameSet(past = past, forecast = forecast))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class RadarPreviewUrls(
    val radarTileUrl: String,
    val baseMapUrl: String,
)

internal fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 1 shl zoom
    val clampedLat = lat.coerceIn(-85.0511, 85.0511)
    val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(clampedLat)
    val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return Pair(x, y)
}

private const val PREVIEW_ZOOM = 6

@Stable
data class RadarFrameSet(
    val past: List<TimedTileUrl>,
    val forecast: List<TimedTileUrl>,
) {
    val allFrames: List<TimedTileUrl> get() = past + forecast
    val totalFrames: Int get() = allFrames.size
}

@Stable
data class TimedTileUrl(
    val timestamp: Long,
    val tileUrl: String,
    val isPast: Boolean,
)
