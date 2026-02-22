package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.repository.RadarFrameSet
import com.sysadmindoc.nimbus.data.repository.RadarRepository
import com.sysadmindoc.nimbus.data.repository.TimedTileUrl
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable
import javax.inject.Inject

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    init {
        loadFrames()
    }

    fun loadFrames() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            radarRepository.getRadarFrames().fold(
                onSuccess = { frameSet ->
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
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            val frameSet = _uiState.value.frameSet ?: return@launch
            val total = frameSet.totalFrames
            if (total < 2) return@launch

            while (true) {
                val currentState = _uiState.value
                val idx = currentState.currentFrameIndex
                val nextIdx = (idx + 1) % total

                _uiState.update { it.copy(currentFrameIndex = nextIdx) }

                // Pause longer on last past frame and last forecast frame
                val isLastPast = nextIdx == frameSet.past.size - 1
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
        if (lat != 0.0 || lon != 0.0) return Pair(lat, lon)
        val saved = prefs.lastLocation.first()
        return if (saved != null) Pair(saved.latitude, saved.longitude) else Pair(39.8, -98.5)
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
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
    val currentFrame: TimedTileUrl?
        get() = frameSet?.allFrames?.getOrNull(currentFrameIndex)

    val totalFrames: Int
        get() = frameSet?.totalFrames ?: 0

    val pastFrameCount: Int
        get() = frameSet?.past?.size ?: 0

    val isCurrentFrameForecast: Boolean
        get() = frameSet?.let { currentFrameIndex >= it.past.size } ?: false
}
