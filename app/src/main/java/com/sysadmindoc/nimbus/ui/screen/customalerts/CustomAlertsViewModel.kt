package com.sysadmindoc.nimbus.ui.screen.customalerts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import com.sysadmindoc.nimbus.util.CustomAlertWorker
import com.sysadmindoc.nimbus.util.convertToCanonical
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Owns the editable rule list for the CustomAlerts screen. Persists each
 * mutation immediately to [UserPreferences] and re-schedules (or cancels)
 * the [CustomAlertWorker] so the periodic check reflects the new state
 * without waiting for the next app cold start.
 */
@HiltViewModel
class CustomAlertsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: UserPreferences,
) : ViewModel() {

    data class UiState(
        val rules: List<CustomAlertRule> = emptyList(),
        val settings: NimbusSettings = NimbusSettings(),
    )

    val uiState: StateFlow<UiState> = combine(prefs.customAlertRules, prefs.settings) { rules, s ->
        UiState(rules = rules, settings = s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /**
     * Build a blank rule seeded with reasonable defaults for the given metric.
     * The threshold is stored in canonical units (Celsius, km/h, mm, UV), so
     * the editor converts on save via [convertToCanonical].
     */
    fun defaultRule(metric: CustomAlertMetric = CustomAlertMetric.TEMP_HIGH_TODAY): CustomAlertRule {
        val defaultCanonical = when (metric) {
            CustomAlertMetric.TEMP_HIGH_TODAY -> 32.0   // 32°C ≈ 90°F
            CustomAlertMetric.TEMP_LOW_TONIGHT -> 0.0
            CustomAlertMetric.WIND_GUST_NEXT_12H -> 50.0
            CustomAlertMetric.PRECIP_SUM_NEXT_24H -> 10.0
            CustomAlertMetric.UV_INDEX_MAX_TODAY -> 8.0
        }
        val op = when (metric) {
            CustomAlertMetric.TEMP_LOW_TONIGHT -> CustomAlertOperator.LESS_THAN
            else -> CustomAlertOperator.GREATER_THAN
        }
        return CustomAlertRule(
            id = UUID.randomUUID().toString(),
            metric = metric,
            operator = op,
            thresholdCanonical = defaultCanonical,
            enabled = true,
        )
    }

    /** Save from the editor — accepts a threshold in the user's display unit. */
    fun upsertRule(
        existing: CustomAlertRule?,
        metric: CustomAlertMetric,
        operator: CustomAlertOperator,
        thresholdInDisplayUnits: Double,
        enabled: Boolean,
    ) = viewModelScope.launch {
        val settings = prefs.settings.first()
        val canonical = convertToCanonical(thresholdInDisplayUnits, metric, settings)
        val next = existing?.copy(
            metric = metric,
            operator = operator,
            thresholdCanonical = canonical,
            enabled = enabled,
        ) ?: CustomAlertRule(
            id = UUID.randomUUID().toString(),
            metric = metric,
            operator = operator,
            thresholdCanonical = canonical,
            enabled = enabled,
        )
        val current = prefs.customAlertRules.first()
        val replaced = current.filter { it.id != next.id } + next
        prefs.setCustomAlertRules(replaced)
        reschedule(replaced)
    }

    fun toggleRule(rule: CustomAlertRule) = viewModelScope.launch {
        val current = prefs.customAlertRules.first()
        val updated = current.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }
        prefs.setCustomAlertRules(updated)
        reschedule(updated)
    }

    fun deleteRule(rule: CustomAlertRule) = viewModelScope.launch {
        val current = prefs.customAlertRules.first()
        val updated = current.filter { it.id != rule.id }
        prefs.setCustomAlertRules(updated)
        reschedule(updated)
    }

    private fun reschedule(rules: List<CustomAlertRule>) {
        if (rules.any { it.enabled }) {
            CustomAlertWorker.schedule(appContext)
        } else {
            CustomAlertWorker.cancel(appContext)
        }
    }
}
