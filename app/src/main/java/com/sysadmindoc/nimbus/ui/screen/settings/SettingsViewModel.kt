package com.sysadmindoc.nimbus.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
) : ViewModel() {

    val settings = prefs.settings

    fun setTempUnit(unit: TempUnit) = viewModelScope.launch { prefs.setTempUnit(unit) }
    fun setWindUnit(unit: WindUnit) = viewModelScope.launch { prefs.setWindUnit(unit) }
    fun setPressureUnit(unit: PressureUnit) = viewModelScope.launch { prefs.setPressureUnit(unit) }
    fun setPrecipUnit(unit: PrecipUnit) = viewModelScope.launch { prefs.setPrecipUnit(unit) }
    fun setTimeFormat(format: TimeFormat) = viewModelScope.launch { prefs.setTimeFormat(format) }
    fun setParticlesEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setParticlesEnabled(enabled) }
}
