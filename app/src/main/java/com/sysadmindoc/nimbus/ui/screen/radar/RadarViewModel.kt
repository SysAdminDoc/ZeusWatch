package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.sysadmindoc.nimbus.data.api.BlitzortungService
import com.sysadmindoc.nimbus.data.api.LightningStrike
import com.sysadmindoc.nimbus.data.model.CommunityReport
import com.sysadmindoc.nimbus.data.model.ReportCondition
import com.sysadmindoc.nimbus.data.repository.CommunityReportSource
import com.sysadmindoc.nimbus.data.repository.RadarFrameSet
import com.sysadmindoc.nimbus.data.repository.RadarRepository
import com.sysadmindoc.nimbus.data.repository.RadarProvider
import com.sysadmindoc.nimbus.data.repository.TimedTileUrl
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable
import javax.inject.Inject

private const val RADAR_FRAME_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
    private val prefs: UserPreferences,
    private val blitzortungService: BlitzortungService,
    private val communityReportRepository: CommunityReportSource,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    /** Settings flow for radar provider selection. */
    val settings = prefs.settings

    /** Offline state derived from ConnectivityObserver. */
    val isOffline: StateFlow<Boolean> = connectivityObserver.isOnline
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Real-time lightning strikes from Blitzortung WebSocket. */
    val lightningStrikes: StateFlow<List<LightningStrike>> = blitzortungService.recentStrikes

    /** Nearby community weather reports. */
    private val _nearbyReports = MutableStateFlow<List<CommunityReport>>(emptyList())
    val nearbyReports: StateFlow<List<CommunityReport>> = _nearbyReports.asStateFlow()

    /** State for the report submission bottom sheet. */
    private val _reportSubmitState = MutableStateFlow(ReportSubmitState())
    val reportSubmitState: StateFlow<ReportSubmitState> = _reportSubmitState.asStateFlow()

    private var playbackJob: Job? = null
    private var frameLoadJob: Job? = null
    private var lastSuccessfulFrameLoadAtMillis: Long? = null

    init {
        loadFrames()
    }

    fun loadFrames(force: Boolean = false) {
        val state = _uiState.value
        if (!shouldLoadRadarFrames(
                state = state,
                isRequestInFlight = frameLoadJob?.isActive == true,
                lastSuccessfulLoadAtMillis = lastSuccessfulFrameLoadAtMillis,
                nowMillis = System.currentTimeMillis(),
                force = force,
            )
        ) {
            return
        }
        frameLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                radarRepository.getRadarFrames().fold(
                    onSuccess = { frameSet ->
                        lastSuccessfulFrameLoadAtMillis = System.currentTimeMillis()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                frameSet = frameSet,
                                currentFrameIndex = (frameSet.past.size - 1).coerceAtLeast(0),
                                error = null,
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message ?: "Failed to load radar")
                        }
                    }
                )
            } finally {
                frameLoadJob = null
            }
        }
    }

    fun togglePlayback() {
        val state = _uiState.value
        if (state.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun seekToFrame(index: Int) {
        val state = _uiState.value
        val frameSet = state.frameSet ?: return
        val clamped = index.coerceIn(0, frameSet.totalFrames - 1)
        _uiState.update { it.copy(currentFrameIndex = clamped) }
    }

    fun onMapInteractionStart() {
        if (_uiState.value.isPlaying) {
            _uiState.update { it.copy(pausedByGesture = true) }
            pausePlayback()
        }
    }

    fun onMapInteractionEnd() {
        if (_uiState.value.pausedByGesture) {
            _uiState.update { it.copy(pausedByGesture = false) }
            startPlayback()
        }
    }

    private fun startPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        val frameSet = _uiState.value.frameSet
        if (!canAnimateRadarPlayback(frameSet)) {
            _uiState.update { it.copy(isPlaying = false) }
            return
        }
        val playableFrameSet = frameSet ?: return
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            val total = playableFrameSet.totalFrames

            while (true) {
                val currentState = _uiState.value
                val idx = currentState.currentFrameIndex
                val nextIdx = (idx + 1) % total

                _uiState.update { it.copy(currentFrameIndex = nextIdx) }

                // Pause longer on last past frame and last forecast frame
                val isLastPast = nextIdx == playableFrameSet.past.size - 1
                val isLastForecast = nextIdx == total - 1
                val delayMs = when {
                    isLastPast -> 1500L
                    isLastForecast -> 2000L
                    else -> 450L
                }
                delay(delayMs)
            }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    /**
     * Resolves lat/lon for the radar map. If both are 0.0 (deep link shortcut),
     * falls back to the last known location from user preferences.
     * Returns Pair(lat, lon) with US center (39.8, -98.5) as ultimate fallback.
     */
    suspend fun resolveLocation(lat: Double, lon: Double): Pair<Double, Double> {
        if (lat != 0.0 && lon != 0.0) return Pair(lat, lon)
        val saved = prefs.lastLocation.first()
        return if (saved != null) Pair(saved.latitude, saved.longitude) else Pair(39.8, -98.5)
    }

    /** Start the Blitzortung WebSocket connection for lightning data. */
    fun connectLightning() {
        blitzortungService.connect()
    }

    /** Stop the Blitzortung WebSocket connection. */
    fun disconnectLightning() {
        blitzortungService.disconnect()
    }

    /** Load community reports near the given location. */
    fun loadNearbyReports(lat: Double, lon: Double) {
        viewModelScope.launch {
            communityReportRepository.getReportsNearby(lat, lon).fold(
                onSuccess = { reports ->
                    _nearbyReports.value = reports
                },
                onFailure = { e ->
                    Log.e("RadarViewModel", "Failed to load community reports", e)
                    _nearbyReports.value = emptyList()
                }
            )
        }
    }

    /** Submit a community weather report at the given location. */
    fun submitReport(lat: Double, lon: Double, condition: ReportCondition, note: String) {
        _reportSubmitState.update { it.copy(isSubmitting = true, result = null) }
        viewModelScope.launch {
            val report = CommunityReport(
                latitude = lat,
                longitude = lon,
                condition = condition,
                note = note,
            )
            communityReportRepository.submitReport(report).fold(
                onSuccess = {
                    _reportSubmitState.update { it.copy(isSubmitting = false, result = "success") }
                    // Refresh nearby reports after submission
                    loadNearbyReports(lat, lon)
                },
                onFailure = { e ->
                    _reportSubmitState.update {
                        it.copy(isSubmitting = false, result = e.message ?: "Submission failed")
                    }
                }
            )
        }
    }

    /** Reset the report submission state (e.g. when sheet is dismissed). */
    fun resetReportState() {
        _reportSubmitState.value = ReportSubmitState()
    }

    fun setRadarProvider(provider: RadarProvider) {
        viewModelScope.launch {
            prefs.setRadarProvider(provider)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        blitzortungService.disconnect()
    }
}

@Stable
data class RadarUiState(
    val isLoading: Boolean = true,
    val frameSet: RadarFrameSet? = null,
    val currentFrameIndex: Int = 0,
    val isPlaying: Boolean = false,
    val pausedByGesture: Boolean = false,
    val error: String? = null,
) {
    val canAnimatePlayback: Boolean
        get() = canAnimateRadarPlayback(frameSet)

    val currentFrame: TimedTileUrl?
        get() = frameSet?.allFrames?.getOrNull(currentFrameIndex)

    val totalFrames: Int
        get() = frameSet?.totalFrames ?: 0

    val pastFrameCount: Int
        get() = frameSet?.past?.size ?: 0

    val isCurrentFrameForecast: Boolean
        get() = frameSet?.let { currentFrameIndex >= it.past.size } ?: false
}

@Stable
data class ReportSubmitState(
    val isSubmitting: Boolean = false,
    val result: String? = null, // null = idle, "success" = done, else error message
)

internal fun canAnimateRadarPlayback(frameSet: RadarFrameSet?): Boolean =
    (frameSet?.totalFrames ?: 0) > 1

internal fun shouldLoadRadarFrames(
    state: RadarUiState,
    isRequestInFlight: Boolean,
    lastSuccessfulLoadAtMillis: Long?,
    nowMillis: Long,
    force: Boolean,
): Boolean {
    if (force) return true
    if (isRequestInFlight) return false
    if (state.frameSet == null) return true
    if (state.error != null) return true
    val lastLoadedAt = lastSuccessfulLoadAtMillis ?: return true
    val delta = nowMillis - lastLoadedAt
    // Treat a negative delta (wall-clock rolled backward, e.g. NTP adjustment)
    // as "stale" so we don't get stuck refusing to refresh for an entire interval.
    return delta < 0L || delta >= RADAR_FRAME_REFRESH_INTERVAL_MS
}
