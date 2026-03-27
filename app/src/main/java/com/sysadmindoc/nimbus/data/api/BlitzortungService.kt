package com.sysadmindoc.nimbus.data.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single lightning strike event from the Blitzortung network.
 */
data class LightningStrike(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val polarity: Int = 0,
)

/**
 * WebSocket client for Blitzortung real-time lightning data.
 *
 * Connects to `wss://ws1.blitzortung.org/` and subscribes to global
 * lightning detection events. Incoming strikes are emitted as a SharedFlow
 * and buffered in [recentStrikes] (last 500 or 10 minutes).
 */
@Singleton
class BlitzortungService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(scopeJob + Dispatchers.IO)

    private val _strikes = MutableSharedFlow<LightningStrike>(extraBufferCapacity = 64)
    val strikes: SharedFlow<LightningStrike> = _strikes.asSharedFlow()

    private val _recentStrikes = MutableStateFlow<List<LightningStrike>>(emptyList())
    val recentStrikes: StateFlow<List<LightningStrike>> = _recentStrikes.asStateFlow()

    private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false

    private val strikeBuffer = mutableListOf<LightningStrike>()
    private val bufferLock = Any()

    /**
     * Open the WebSocket connection and begin receiving lightning data.
     * Safe to call multiple times — reconnects only if not already connected.
     */
    @Synchronized
    fun connect() {
        if (isConnected) return

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        // Blitzortung needs a plain client without the logging/UA interceptors
        // that add latency to the high-frequency WebSocket stream.
        val wsClient = okHttpClient.newBuilder()
            .retryOnConnectionFailure(true)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Blitzortung WebSocket connected")
                isConnected = true
                // Subscribe to global lightning data (area 11 = worldwide)
                webSocket.send(SUBSCRIBE_MESSAGE)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseStrike(text)?.let { strike ->
                    addToBuffer(strike)
                    scope.launch { _strikes.emit(strike) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Blitzortung WebSocket failure: ${t.message}")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Blitzortung WebSocket closed: $reason")
                isConnected = false
            }
        })
    }

    /**
     * Close the WebSocket connection and clear the strike buffer.
     */
    @Synchronized
    fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE, "User navigated away")
        webSocket = null
        isConnected = false
        // Cancel outstanding coroutines and recreate the scope
        scopeJob.cancel()
        scopeJob = SupervisorJob()
        scope = CoroutineScope(scopeJob + Dispatchers.IO)
        synchronized(bufferLock) {
            strikeBuffer.clear()
            _recentStrikes.value = emptyList()
        }
    }

    private fun parseStrike(json: String): LightningStrike? {
        return try {
            val obj = JSONObject(json)
            // time is in nanoseconds; convert to epoch millis
            val timeNanos = obj.optLong("time", 0L)
            if (timeNanos == 0L) return null
            val timestampMs = timeNanos / 1_000_000

            LightningStrike(
                lat = obj.getDouble("lat"),
                lon = obj.getDouble("lon"),
                timestamp = timestampMs,
                polarity = obj.optInt("pol", 0),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse lightning strike: ${e.message}")
            null
        }
    }

    private fun addToBuffer(strike: LightningStrike) {
        synchronized(bufferLock) {
            strikeBuffer.add(strike)

            // Evict strikes older than 10 minutes
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            strikeBuffer.removeAll { it.timestamp < cutoff }

            // Cap at MAX_BUFFER_SIZE
            while (strikeBuffer.size > MAX_BUFFER_SIZE) {
                strikeBuffer.removeAt(0)
            }

            _recentStrikes.value = strikeBuffer.toList()
        }
    }

    companion object {
        private const val TAG = "BlitzortungService"
        private const val WS_URL = "wss://ws1.blitzortung.org/"
        private const val SUBSCRIBE_MESSAGE = """{"a":11}"""
        private const val NORMAL_CLOSURE = 1000
        private const val MAX_BUFFER_SIZE = 500
        private const val MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes
    }
}
