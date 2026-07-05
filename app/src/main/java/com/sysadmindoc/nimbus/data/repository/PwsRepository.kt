package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.di.DefaultDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private const val TEMPEST_WS_URL = "wss://ws.weatherflow.com/swd/data"
private const val TEMPEST_OBSERVATION_TIMEOUT_MS = 75_000L
private const val METERS_PER_SECOND_TO_KMH = 3.6

data class PwsObservation(
    val sourceLabel: String = "WeatherFlow Tempest",
    val observedAt: LocalDateTime,
    val temperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windGustKmh: Double?,
    val windDirectionDegrees: Int?,
    val pressureHpa: Double?,
    val uvIndex: Double?,
    val rainLastMinuteMm: Double?,
    val precipitationType: TempestPrecipitationType,
    val lightningStrikeCount: Int?,
    val lightningStrikeAverageDistanceKm: Double?,
    val reportIntervalMinutes: Int?,
)

enum class TempestPrecipitationType {
    NONE,
    RAIN,
    HAIL,
    RAIN_AND_HAIL,
    UNKNOWN,
}

class TempestPwsConfigurationException(message: String) : IllegalArgumentException(message)

@Singleton
class PwsRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend fun fetchLatestObservation(settings: NimbusSettings): Result<PwsObservation> {
        val token = settings.tempestAccessToken.trim()
        val deviceId = settings.tempestDeviceId.trim().toLongOrNull()
        if (token.isBlank()) {
            return Result.failure(TempestPwsConfigurationException("Tempest access token is required."))
        }
        if (deviceId == null || deviceId <= 0) {
            return Result.failure(TempestPwsConfigurationException("Tempest device ID is required."))
        }

        return try {
            Result.success(
                withContext(defaultDispatcher) {
                    withTimeout(TEMPEST_OBSERVATION_TIMEOUT_MS) {
                        TempestWebSocketService(
                            okHttpClient = okHttpClient,
                            accessToken = token,
                            deviceId = deviceId,
                        ).awaitObservation()
                    }
                },
            )
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Result.failure(failure)
        }
    }
}

internal class TempestWebSocketService(
    private val okHttpClient: OkHttpClient,
    private val accessToken: String,
    private val deviceId: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun awaitObservation(): PwsObservation = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        var webSocket: WebSocket? = null
        val request = Request.Builder()
            .url("$TEMPEST_WS_URL?token=${accessToken.urlEncoded()}")
            .build()
        val listenRequestId = "zeuswatch-${System.currentTimeMillis()}"

        fun closeSocket() {
            webSocket?.close(1000, "observation received")
        }

        fun complete(observation: PwsObservation) {
            if (completed.compareAndSet(false, true)) {
                continuation.resume(observation)
                closeSocket()
            }
        }

        fun fail(cause: Throwable) {
            if (completed.compareAndSet(false, true)) {
                continuation.resumeWithException(cause)
                webSocket?.cancel()
            }
        }

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"listen_start","device_id":$deviceId,"id":"$listenRequestId"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    TempestObservationParser.parse(text, zoneId)?.let(::complete)
                    TempestObservationParser.errorMessage(text)?.let { fail(IOException(it)) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    fail(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    fail(IOException("Tempest WebSocket closed before an observation arrived: $code $reason"))
                }
            },
        )
        continuation.invokeOnCancellation {
            if (completed.compareAndSet(false, true)) {
                webSocket.close(1000, "cancelled")
            }
        }
    }
}

internal object TempestObservationParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(message: String, zoneId: ZoneId = ZoneId.systemDefault()): PwsObservation? {
        val root = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return null
        if (root["type"]?.jsonPrimitive?.contentOrNull != "obs_st") return null
        val obs = root["obs"]?.jsonArray?.firstOrNull()?.jsonArray ?: return null
        val timestamp = obs.longAt(0) ?: return null
        val temperatureC = obs.doubleAt(7) ?: return null
        val humidity = obs.doubleAt(8)?.roundToInt()?.coerceIn(0, 100) ?: return null
        return PwsObservation(
            observedAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zoneId),
            temperatureC = temperatureC,
            humidityPercent = humidity,
            windSpeedKmh = (obs.doubleAt(2) ?: 0.0) * METERS_PER_SECOND_TO_KMH,
            windGustKmh = obs.doubleAt(3)?.times(METERS_PER_SECOND_TO_KMH),
            windDirectionDegrees = obs.intAt(4),
            pressureHpa = obs.doubleAt(6),
            uvIndex = obs.doubleAt(10),
            rainLastMinuteMm = obs.doubleAt(12),
            precipitationType = precipitationType(obs.intAt(13)),
            lightningStrikeAverageDistanceKm = obs.doubleAt(14),
            lightningStrikeCount = obs.intAt(15),
            reportIntervalMinutes = obs.intAt(17),
        )
    }

    fun errorMessage(message: String): String? {
        val root = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return null
        val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return null
        if (type != "error") return null
        return root["message"]?.jsonPrimitive?.contentOrNull ?: "Tempest rejected the WebSocket request."
    }

    private fun precipitationType(code: Int?): TempestPrecipitationType = when (code) {
        0, null -> TempestPrecipitationType.NONE
        1 -> TempestPrecipitationType.RAIN
        2 -> TempestPrecipitationType.HAIL
        3 -> TempestPrecipitationType.RAIN_AND_HAIL
        else -> TempestPrecipitationType.UNKNOWN
    }

    private fun JsonArray.doubleAt(index: Int): Double? =
        getOrNull(index)?.jsonPrimitive?.doubleOrNull

    private fun JsonArray.intAt(index: Int): Int? =
        getOrNull(index)?.jsonPrimitive?.intOrNull

    private fun JsonArray.longAt(index: Int): Long? =
        getOrNull(index)?.jsonPrimitive?.longOrNull
}

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
