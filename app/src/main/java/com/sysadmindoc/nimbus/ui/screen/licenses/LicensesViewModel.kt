package com.sysadmindoc.nimbus.ui.screen.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.repository.OssNotices
import com.sysadmindoc.nimbus.data.repository.OssNoticesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LicensesViewModel @Inject constructor(
    private val repository: OssNoticesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LicensesUiState())
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    fun load() {
        // Idempotent: the screen calls this on every composition of its
        // LaunchedEffect, and the asset never changes at runtime.
        if (!_uiState.value.notices.isEmpty) return
        viewModelScope.launch {
            _uiState.value = LicensesUiState(notices = repository.load())
        }
    }
}

data class LicensesUiState(
    val notices: OssNotices = OssNotices(),
)
