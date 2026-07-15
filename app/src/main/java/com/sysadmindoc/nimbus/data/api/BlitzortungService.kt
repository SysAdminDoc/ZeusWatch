package com.sysadmindoc.nimbus.data.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val _strikes = MutableSharedFlow<LightningStrike>(extraBufferCapacity = 64)
    val strikes: SharedFlow<LightningStrike> = _strikes.asSharedFlow()

    private val _recentStrikes = MutableStateFlow<List<LightningStrike>>(emptyList())
    val recentStrikes: StateFlow<List<LightningStrike>> = _recentStrikes.asStateFlow()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    @Volatile private var shouldReconnect = false
    @Volatile private var reconnectAttempts = 0
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var activeClients = 0

    private val strikeBuffer = mutableListOf<LightningStrike>()
    private val bufferLock = Any()
    @Volatile private var lastEmitTime = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val webSocketClient: OkHttpClient = okHttpClient.newBuilder()
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Acquire a reference on the shared WebSocket connection and begin
     * receiving lightning data. The service is a singleton shared by multiple
     * radar surfaces (tablet layouts compose RadarTab and RadarScreen
     * simultaneously), so each [connect] must be balanced by one [disconnect];
     * the socket is only torn down when the last reference is released.
     */
    @Synchronized
    fun connect() {
        activeClients++
        openSocketLocked()
    }

    /**
     * Open the socket if one isn't already pending or connected.
     *
     * Gating on `webSocket != null` (rather than `isConnected`) closes a
     * window where the WS has been created but `onOpen` hasn't flipped
     * `isConnected` yet: a second call entering during that window would
     * otherwise leak the in-flight socket and start a second subscription,
     * doubling Blitzortung's incoming message rate.
     */
    @Synchronized
    private fun openSocketLocked() {
        if (webSocket != null) return
        shouldReconnect = true

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        // Blitzortung needs a plain client without the logging/UA interceptors
        // that add latency to the high-frequency WebSocket stream.
        webSocket = webSocketClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Blitzortung WebSocket connected")
                isConnected = true
                reconnectAttempts = 0
                reconnectJob?.cancel()
                reconnectJob = null
                // Subscribe to global lightning data (area 11 = worldwide)
                webSocket.send(SUBSCRIBE_MESSAGE)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseStrike(text)?.let { strike ->
                    addToBuffer(strike)
                    _strikes.tryEmit(strike)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Blitzortung WebSocket failure: ${t.message}")
                // A handshake that completed before failing hands back a Response
                // whose body the caller must close, or it leaks a connection under
                // repeated backoff reconnects.
                response?.close()
                onSocketTerminated(webSocket, reconnect = true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Blitzortung WebSocket closed: $reason")
                onSocketTerminated(webSocket, reconnect = code != NORMAL_CLOSURE)
            }
        })
    }

    /**
     * Clear connection state only if the terminating socket is still the active one.
     * A late onClosed/onFailure from a socket replaced by a newer connect() must not
     * null out the newer socket, or the next connect() would open a duplicate stream.
     */
    @Synchronized
    private fun onSocketTerminated(socket: WebSocket, reconnect: Boolean) {
        if (webSocket === socket) {
            isConnected = false
            webSocket = null
            if (reconnect && shouldReconnect) {
                scheduleReconnect()
            }
        }
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        val delayMs = blitzortungReconnectDelayMs(reconnectAttempts)
        reconnectAttempts++
        reconnectJob = serviceScope.launch {
            delay(delayMs)
            performReconnect()
        }
    }

    /**
     * Clear the completed reconnect job and reconnect — but only under the monitor
     * and only if a concurrent [disconnect] hasn't since cleared [shouldReconnect].
     * Running this off-lock would race disconnect() into a duplicate socket.
     */
    @Synchronized
    private fun performReconnect() {
        reconnectJob = null
        if (shouldReconnect) {
            // Reopen the socket directly — going through connect() would take
            // an extra client reference the reconnect never releases.
            openSocketLocked()
        }
    }

    /**
     * Release one reference on the connection. Closes the WebSocket and clears
     * the strike buffer only when the last client disconnects, so one radar
     * surface leaving composition cannot cut lightning off for another.
     */
    @Synchronized
    fun disconnect() {
        if (activeClients > 0) activeClients--
        if (activeClients > 0) return
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        webSocket?.close(NORMAL_CLOSURE, "User navigated away")
        webSocket = null
        isConnected = false
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

            // The live stream interleaves non-strike frames (keepalives/sferics)
            // that carry no coordinates. Use optDouble + NaN check so those skip
            // quietly instead of throwing JSONException on every frame.
            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null

            LightningStrike(
                lat = lat,
                lon = lon,
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

            // Time-gated only: a batch-size OR trigger defeated the 1s throttle
            // at high strike rates (storm peaks pushed 4-40 emits/s straight
            // into recomposition).
            val now = System.currentTimeMillis()
            if (now - lastEmitTime >= EMIT_THROTTLE_MS) {
                _recentStrikes.value = strikeBuffer.toList()
                lastEmitTime = now
            }
        }
    }

    companion object {
        private const val TAG = "BlitzortungService"
        private const val WS_URL = "wss://ws1.blitzortung.org/"
        private const val SUBSCRIBE_MESSAGE = """{"a":11}"""
        private const val NORMAL_CLOSURE = 1000
        private const val MAX_BUFFER_SIZE = 500
        private const val MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes
        private const val EMIT_THROTTLE_MS = 1_000L
    }
}

internal fun blitzortungReconnectDelayMs(attempt: Int): Long {
    val shift = attempt.coerceIn(0, 5)
    return 1_000L * (1L shl shift)
}
