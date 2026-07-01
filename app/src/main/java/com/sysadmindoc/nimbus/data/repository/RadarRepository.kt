package com.sysadmindoc.nimbus.data.repository

import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.api.RadarFrame
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

@Singleton
class RadarRepository @Inject constructor(
    @Named("rainviewer") private val rainViewerApi: RainViewerApi,
    @Named("librewxr") private val libreWxrApi: RainViewerApi,
) {
    fun buildPreviewTileUrls(
        lat: Double,
        lon: Double,
        tileUrlTemplate: String,
        zoom: Int = PREVIEW_ZOOM,
        maxTileZoom: Int = RainViewerApi.MAX_TILE_ZOOM,
    ): RadarPreviewUrls {
        val supportedZoom = zoom.coerceIn(MIN_RADAR_TILE_ZOOM, maxTileZoom)
        val (tileX, tileY) = latLonToTile(lat, lon, supportedZoom)
        val radarUrl = tileUrlTemplate
            .replace("{z}", supportedZoom.toString())
            .replace("{x}", tileX.toString())
            .replace("{y}", tileY.toString())
        val baseMapUrl = "https://basemaps.cartocdn.com/dark_all/$supportedZoom/$tileX/$tileY@2x.png"
        return RadarPreviewUrls(radarUrl, baseMapUrl)
    }

    suspend fun getRadarFrames(
        provider: RadarProvider = RadarProvider.NATIVE_MAPLIBRE,
    ): Result<RadarFrameSet> = withContext(Dispatchers.IO) {
        try {
            val frameSet = if (provider == RadarProvider.LIBREWXR_NATIVE) {
                loadLibreWxrFrames().getOrElse {
                    loadRainViewerFrames().getOrThrow()
                }
            } else {
                loadRainViewerFrames().getOrThrow()
            }
            Result.success(frameSet)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun loadRainViewerFrames(): Result<RadarFrameSet> {
        return try {
            val response = rainViewerApi.getWeatherMaps()
            val radar = response.radar ?: return Result.failure(Exception("No radar data available"))
            if (radar.past.isEmpty()) {
                return Result.failure(Exception("No supported RainViewer past radar frames available"))
            }
            val past = radar.past.map { frame ->
                TimedTileUrl(
                    timestamp = frame.time,
                    tileUrl = RainViewerApi.buildTileUrl(frame.path, response.host),
                    isPast = true,
                )
            }
            Result.success(
                RadarFrameSet(
                    past = past,
                    forecast = emptyList(),
                    source = RadarTileSource.RAINVIEWER,
                    maxTileZoom = RainViewerApi.MAX_TILE_ZOOM,
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun loadLibreWxrFrames(): Result<RadarFrameSet> {
        return try {
            val response = libreWxrApi.getWeatherMaps()
            val radar = response.radar ?: return Result.failure(Exception("No LibreWXR radar data available"))
            if (radar.past.isEmpty()) {
                return Result.failure(Exception("No LibreWXR past radar frames available"))
            }
            val host = response.host?.takeIf { it.isNotBlank() } ?: RainViewerApi.LIBREWXR_BASE_URL.trimEnd('/')
            val past = radar.past.map { frame ->
                frame.toLibreWxrTile(isPast = true, host = host)
            }
            val forecast = radar.nowcast.map { frame ->
                frame.toLibreWxrTile(isPast = false, host = host)
            }
            Result.success(
                RadarFrameSet(
                    past = past,
                    forecast = forecast,
                    source = RadarTileSource.LIBREWXR,
                    maxTileZoom = RainViewerApi.LIBREWXR_MAX_TILE_ZOOM,
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun RadarFrame.toLibreWxrTile(isPast: Boolean, host: String): TimedTileUrl =
        TimedTileUrl(
            timestamp = time,
            tileUrl = RainViewerApi.buildTileUrl(
                path = path,
                host = host,
                colorScheme = RainViewerApi.LIBREWXR_VIPER_HD_COLOR_SCHEME,
                restrictToPublicRainViewer = false,
            ),
            isPast = isPast,
        )
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

private const val MIN_RADAR_TILE_ZOOM = 0
private const val PREVIEW_ZOOM = 6

enum class RadarTileSource {
    RAINVIEWER,
    LIBREWXR,
}

@Stable
data class RadarFrameSet(
    val past: List<TimedTileUrl>,
    val forecast: List<TimedTileUrl>,
    val source: RadarTileSource = RadarTileSource.RAINVIEWER,
    val maxTileZoom: Int = RainViewerApi.MAX_TILE_ZOOM,
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
