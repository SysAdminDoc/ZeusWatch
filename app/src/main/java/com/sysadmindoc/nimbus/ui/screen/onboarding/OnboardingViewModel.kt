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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {
    val onboardingComplete = prefs.settings
        .map<NimbusSettings, Boolean?> { it.onboardingComplete }
        .distinctUntilChanged()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun complete(
        tempUnit: TempUnit,
        starterCardSet: StarterCardSet,
        onComplete: () -> Unit,
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            prefs.completeOnboarding(tempUnit, starterCardSet)
            _isSaving.value = false
            onComplete()
        }
    }
}
