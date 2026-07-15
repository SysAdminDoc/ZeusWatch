package com.sysadmindoc.nimbus.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.StarterCardSet
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {
    val onboardingComplete = prefs.settings
        .map<NimbusSettings, Boolean?> { it.onboardingComplete }
        .distinctUntilChanged()

    private val _saveState = MutableStateFlow(OnboardingSaveState())
    val saveState: StateFlow<OnboardingSaveState> = _saveState.asStateFlow()

    fun complete(
        tempUnit: TempUnit,
        starterCardSet: StarterCardSet,
        onComplete: () -> Unit,
    ) {
        while (true) {
            val state = _saveState.value
            if (state.isSaving) return
            if (_saveState.compareAndSet(state, state.copy(isSaving = true, saveFailed = false))) break
        }
        viewModelScope.launch {
            try {
                prefs.completeOnboarding(tempUnit, starterCardSet)
            } catch (error: CancellationException) {
                _saveState.value = OnboardingSaveState()
                throw error
            } catch (_: Exception) {
                _saveState.value = OnboardingSaveState(saveFailed = true)
                return@launch
            }
            try {
                onComplete()
            } finally {
                _saveState.value = OnboardingSaveState()
            }
        }
    }
}

data class OnboardingSaveState(
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)
