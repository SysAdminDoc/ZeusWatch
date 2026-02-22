package com.sysadmindoc.nimbus.data.repository

import androidx.compose.runtime.Stable
import com.sysadmindoc.nimbus.data.api.RadarFrame
import com.sysadmindoc.nimbus.data.api.RainViewerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadarRepository @Inject constructor(
    private val rainViewerApi: RainViewerApi,
) {
    suspend fun getRadarFrames(): Result<RadarFrameSet> = withContext(Dispatchers.IO) {
        try {
            val response = rainViewerApi.getWeatherMaps()
            val radar = response.radar ?: return@withContext Result.failure(
                Exception("No radar data available")
            )
            val past = radar.past.map { frame ->
                TimedTileUrl(
                    timestamp = frame.time,
                    tileUrl = RainViewerApi.buildTileUrl(frame.path),
                    isPast = true,
                )
            }
            val forecast = radar.nowcast.map { frame ->
                TimedTileUrl(
                    timestamp = frame.time,
                    tileUrl = RainViewerApi.buildTileUrl(frame.path),
                    isPast = false,
                )
            }
            Result.success(RadarFrameSet(past = past, forecast = forecast))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

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
