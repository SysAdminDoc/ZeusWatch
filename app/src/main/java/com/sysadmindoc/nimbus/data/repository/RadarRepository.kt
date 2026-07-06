package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.api.RadarFrame
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

@Singleton
class RadarRepository @Inject constructor(
    @param:Named("rainviewer") private val rainViewerApi: RainViewerApi,
    @param:Named("librewxr") private val libreWxrApi: RainViewerApi,
    private val frameCache: RadarFrameMetadataCache = NoopRadarFrameMetadataCache,
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
        cacheOnly: Boolean = false,
    ): Result<RadarFrameSet> = withContext(Dispatchers.IO) {
        if (cacheOnly) {
            return@withContext frameCache.read(provider)
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("No cached radar frames available"))
        }

        try {
            val frameSet = if (provider == RadarProvider.LIBREWXR_NATIVE) {
                loadLibreWxrFrames().getOrElse {
                    loadRainViewerFrames().getOrThrow()
                }
            } else {
                loadRainViewerFrames().getOrThrow()
            }
            frameCache.write(provider, frameSet)
            Result.success(frameSet)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            frameCache.read(provider)
                ?.let { Result.success(it) }
                ?: Result.failure(e)
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

interface RadarFrameMetadataCache {
    fun read(provider: RadarProvider): RadarFrameSet?
    fun write(provider: RadarProvider, frameSet: RadarFrameSet)
}

private object NoopRadarFrameMetadataCache : RadarFrameMetadataCache {
    override fun read(provider: RadarProvider): RadarFrameSet? = null
    override fun write(provider: RadarProvider, frameSet: RadarFrameSet) = Unit
}

@Singleton
class SharedPreferencesRadarFrameMetadataCache @Inject constructor(
    @ApplicationContext context: Context,
) : RadarFrameMetadataCache {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun read(provider: RadarProvider): RadarFrameSet? {
        val raw = prefs.getString(cacheKey(provider), null) ?: return null
        return runCatching {
            val cached = json.decodeFromString(CachedRadarFrameSet.serializer(), raw)
            cached.toFrameSetIfFresh(nowMillis = System.currentTimeMillis())
        }.getOrElse {
            prefs.edit().remove(cacheKey(provider)).apply()
            null
        }
    }

    override fun write(provider: RadarProvider, frameSet: RadarFrameSet) {
        val payload = frameSet.toCachedRadarFrameSet(cachedAtMillis = System.currentTimeMillis())
        val raw = json.encodeToString(CachedRadarFrameSet.serializer(), payload)
        prefs.edit().putString(cacheKey(provider), raw).apply()
    }

    private fun cacheKey(provider: RadarProvider): String = "frame_set_${provider.name}"

    private companion object {
        const val PREFS_NAME = "radar_frame_metadata_cache"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RadarFrameMetadataCacheModule {
    @Provides
    @Singleton
    fun provideRadarFrameMetadataCache(
        @ApplicationContext context: Context,
    ): RadarFrameMetadataCache = SharedPreferencesRadarFrameMetadataCache(context)
}

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
    val isFromCache: Boolean = false,
    val cachedAtMillis: Long? = null,
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

@Serializable
internal data class CachedRadarFrameSet(
    val cachedAtMillis: Long,
    val source: RadarTileSource,
    val maxTileZoom: Int,
    val past: List<CachedTimedTileUrl>,
    val forecast: List<CachedTimedTileUrl>,
)

@Serializable
internal data class CachedTimedTileUrl(
    val timestamp: Long,
    val tileUrl: String,
    val isPast: Boolean,
)

internal fun RadarFrameSet.toCachedRadarFrameSet(cachedAtMillis: Long): CachedRadarFrameSet =
    CachedRadarFrameSet(
        cachedAtMillis = cachedAtMillis,
        source = source,
        maxTileZoom = maxTileZoom,
        past = past.map { it.toCachedTimedTileUrl() },
        forecast = forecast.map { it.toCachedTimedTileUrl() },
    )

internal fun CachedRadarFrameSet.toFrameSetIfFresh(
    nowMillis: Long,
    maxAgeMillis: Long = RADAR_FRAME_CACHE_MAX_AGE_MS,
): RadarFrameSet? {
    if (nowMillis - cachedAtMillis > maxAgeMillis) return null
    return RadarFrameSet(
        past = past.map { it.toTimedTileUrl() },
        forecast = forecast.map { it.toTimedTileUrl() },
        source = source,
        maxTileZoom = maxTileZoom,
        isFromCache = true,
        cachedAtMillis = cachedAtMillis,
    )
}

private fun TimedTileUrl.toCachedTimedTileUrl(): CachedTimedTileUrl =
    CachedTimedTileUrl(
        timestamp = timestamp,
        tileUrl = tileUrl,
        isPast = isPast,
    )

private fun CachedTimedTileUrl.toTimedTileUrl(): TimedTileUrl =
    TimedTileUrl(
        timestamp = timestamp,
        tileUrl = tileUrl,
        isPast = isPast,
    )

private const val RADAR_FRAME_CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L
