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
internal const val MAX_CUSTOM_ALERT_RULES = 50

@HiltViewModel
class CustomAlertsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: UserPreferences,
) : ViewModel() {

    data class UiState(
        val rules: List<CustomAlertRule> = emptyList(),
        val settings: NimbusSettings = NimbusSettings(),
        val isAtRuleCap: Boolean = false,
    )

    val uiState: StateFlow<UiState> = combine(prefs.customAlertRules, prefs.settings) { rules, s ->
        UiState(rules = rules, settings = s, isAtRuleCap = rules.size >= MAX_CUSTOM_ALERT_RULES)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /**
     * Build a blank rule seeded with reasonable defaults for the given metric.
     * The threshold is stored in canonical units (Celsius, km/h, mm, UV), so
     * the editor converts on save via [convertToCanonical].
     */
    fun defaultRule(metric: CustomAlertMetric = CustomAlertMetric.TEMP_HIGH_TODAY): CustomAlertRule {
        return CustomAlertRule(
            id = UUID.randomUUID().toString(),
            metric = metric,
            operator = defaultOperator(metric),
            thresholdCanonical = defaultThresholdCanonical(metric),
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
        // Atomic RMW (single store.edit) so quick successive mutations can't
        // lose each other's writes; map-replace keeps an edited rule at its
        // existing position instead of reordering it to the bottom.
        val replaced = prefs.updateCustomAlertRules { current ->
            if (current.any { it.id == next.id }) {
                current.map { if (it.id == next.id) next else it }
            } else if (current.size >= MAX_CUSTOM_ALERT_RULES) {
                current
            } else {
                current + next
            }
        }
        reschedule(replaced)
    }

    fun toggleRule(rule: CustomAlertRule) = viewModelScope.launch {
        val updated = prefs.updateCustomAlertRules { current ->
            current.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }
        }
        reschedule(updated)
    }

    fun deleteRule(rule: CustomAlertRule) = viewModelScope.launch {
        val updated = prefs.updateCustomAlertRules { current ->
            current.filter { it.id != rule.id }
        }
        reschedule(updated)
    }

    /** Undo a delete: re-insert [rule] at its previous [position] (clamped). */
    fun restoreRule(rule: CustomAlertRule, position: Int) = viewModelScope.launch {
        val updated = prefs.updateCustomAlertRules { current ->
            if (current.any { it.id == rule.id }) {
                current
            } else {
                current.toMutableList().apply {
                    add(position.coerceIn(0, size), rule)
                }
            }
        }
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

internal fun defaultThresholdCanonical(metric: CustomAlertMetric): Double = when (metric) {
    CustomAlertMetric.TEMP_HIGH_TODAY -> 32.0   // 32°C ≈ 90°F
    CustomAlertMetric.TEMP_LOW_TONIGHT -> 0.0
    CustomAlertMetric.WIND_GUST_NEXT_12H -> 50.0
    CustomAlertMetric.PRECIP_SUM_NEXT_24H -> 10.0
    CustomAlertMetric.UV_INDEX_MAX_TODAY -> 8.0
    CustomAlertMetric.DEW_POINT_NOW -> 20.0     // 20°C = muggy threshold
    CustomAlertMetric.FEELS_LIKE_NOW -> 35.0    // 35°C ≈ 95°F danger zone
    CustomAlertMetric.SNOWFALL_SUM_NEXT_24H -> 5.0
    CustomAlertMetric.PRESSURE_NOW -> 1000.0    // hPa — storm threshold
    CustomAlertMetric.AQI_NOW -> 100.0          // US EPA "Unhealthy for Sensitive Groups"
}

internal fun defaultOperator(metric: CustomAlertMetric): CustomAlertOperator = when (metric) {
    CustomAlertMetric.TEMP_LOW_TONIGHT -> CustomAlertOperator.LESS_THAN
    else -> CustomAlertOperator.GREATER_THAN
}
