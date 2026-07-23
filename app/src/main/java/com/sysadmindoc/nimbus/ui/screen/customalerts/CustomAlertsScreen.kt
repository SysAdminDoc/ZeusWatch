package com.sysadmindoc.nimbus.ui.screen.customalerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.component.NimbusScrollableSegmentRow
import com.sysadmindoc.nimbus.ui.component.NimbusSelectableSegment
import com.sysadmindoc.nimbus.ui.component.NimbusStatusBadge
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.PremiumMessageCard
import com.sysadmindoc.nimbus.ui.component.ScreenHeader
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusError
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.convertForDisplay
import com.sysadmindoc.nimbus.util.displayUnitLabel
import com.sysadmindoc.nimbus.util.labelRes
import com.sysadmindoc.nimbus.util.summaryRes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAlertsScreen(
    onBack: () -> Unit,
    viewModel: CustomAlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Saveable so a rotation (or process death) mid-edit keeps the open rule.
    var editing by rememberSaveable(stateSaver = EditorStateSaver) {
        mutableStateOf<EditorState?>(null)
    }
    val addCustomAlertDescription = stringResource(R.string.custom_alerts_add_cd)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val capReachedMessage = stringResource(R.string.custom_alerts_cap_reached, MAX_CUSTOM_ALERT_RULES)
    val showCapReached: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = capReachedMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }
    // The 50-rule cap must never fail silently: at cap the add path surfaces
    // the limit instead of opening an editor whose save would be discarded.
    val startNewAlert = {
        if (state.isAtRuleCap) {
            showCapReached()
        } else {
            editing = EditorState(existing = null, draft = viewModel.defaultRule())
        }
    }
    val deletedMessage = stringResource(R.string.custom_alerts_deleted)
    val undoLabel = stringResource(R.string.common_undo)
    // Delete is immediate (no confirmation dialog) — the snackbar provides
    // feedback and an Undo that restores the rule at its old position.
    val deleteWithUndo: (CustomAlertRule) -> Unit = { rule ->
        val position = state.rules.indexOfFirst { it.id == rule.id }
        viewModel.deleteRule(rule)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreRule(rule, position)
            }
        }
    }

    PredictiveBackScaffold(onBack = onBack) {
        Scaffold(
            containerColor = NimbusNavyDark,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ScreenHeader(
                    title = stringResource(R.string.custom_alerts_title),
                    subtitle = stringResource(R.string.custom_alerts_subtitle),
                    eyebrow = stringResource(R.string.custom_alerts_eyebrow),
                    onBack = onBack,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
            floatingActionButton = {
                if (editing == null) {
                    FloatingActionButton(
                        onClick = startNewAlert,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .navigationBarsPadding()
                            .semantics { contentDescription = addCustomAlertDescription },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            },
        ) { innerPadding ->
            if (state.rules.isEmpty()) {
                EmptyState(
                    onAdd = startNewAlert,
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                )
            } else {
                RuleList(
                    rules = state.rules,
                    settings = state.settings,
                    onToggle = { viewModel.toggleRule(it) },
                    onEdit = { editing = EditorState(existing = it, draft = it) },
                    onDelete = deleteWithUndo,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()
    editing?.let { ed ->
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
            containerColor = NimbusCardBg,
        ) {
            RuleEditor(
                initial = ed.draft,
                isNew = ed.existing == null,
                settings = state.settings,
                onSave = { metric, op, threshold, enabled ->
                    if (ed.existing == null && state.isAtRuleCap) {
                        // Cap reached while the sheet was open — keep the
                        // draft on screen instead of closing as if saved.
                        showCapReached()
                    } else {
                        viewModel.upsertRule(
                            existing = ed.existing,
                            metric = metric,
                            operator = op,
                            thresholdInDisplayUnits = threshold,
                            enabled = enabled,
                        )
                        editing = null
                    }
                },
                onCancel = { editing = null },
            )
        }
    }
}

private data class EditorState(
    val existing: CustomAlertRule?,
    val draft: CustomAlertRule,
)

private val editorStateJson = Json { ignoreUnknownKeys = true }

/**
 * Saver for the open-editor state: [CustomAlertRule] is already
 * kotlinx-serializable (it persists to preferences), so both halves round-trip
 * through JSON strings. `emptyList` encodes the "no editor open" state.
 */
private val EditorStateSaver = listSaver<EditorState?, String>(
    save = { state ->
        if (state == null) {
            emptyList()
        } else {
            listOf(
                state.existing?.let {
                    editorStateJson.encodeToString(CustomAlertRule.serializer(), it)
                } ?: "",
                editorStateJson.encodeToString(CustomAlertRule.serializer(), state.draft),
            )
        }
    },
    restore = { saved ->
        val draft = saved.getOrNull(1)?.let { raw ->
            runCatching {
                editorStateJson.decodeFromString(CustomAlertRule.serializer(), raw)
            }.getOrNull()
        }
        if (draft == null) {
            null
        } else {
            val existing = saved.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let { raw ->
                runCatching {
                    editorStateJson.decodeFromString(CustomAlertRule.serializer(), raw)
                }.getOrNull()
            }
            EditorState(existing = existing, draft = draft)
        }
    },
)

@Composable
private fun RuleList(
    rules: List<CustomAlertRule>,
    settings: NimbusSettings,
    onToggle: (CustomAlertRule) -> Unit,
    onEdit: (CustomAlertRule) -> Unit,
    onDelete: (CustomAlertRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CustomAlertsIntroCard(ruleCount = rules.size)
        }
        items(rules, key = { it.id }) { rule ->
            RuleRow(
                rule = rule,
                settings = settings,
                onToggle = { onToggle(rule) },
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
            )
        }
    }
}

@Composable
private fun EmptyState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        PremiumMessageCard(
            title = stringResource(R.string.custom_alerts_empty_title),
            message = stringResource(R.string.custom_alerts_empty_message),
            icon = Icons.Filled.Notifications,
            primaryActionLabel = stringResource(R.string.custom_alerts_empty_action),
            onPrimaryAction = onAdd,
        )
    }
}

@Composable
private fun RuleRow(
    rule: CustomAlertRule,
    settings: NimbusSettings,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val metricLabel = stringResource(rule.metric.labelRes)
    val metricSummary = stringResource(rule.metric.summaryRes)
    val operatorLabel = stringResource(rule.operator.labelRes)
    val threshold = formatThreshold(rule, settings)
    val ruleLabel = "$metricLabel ${rule.operator.symbol} $threshold"
    val ruleDescription = "$metricLabel $operatorLabel $threshold"
    val ruleState = if (rule.enabled) {
        stringResource(R.string.custom_alerts_state_active)
    } else {
        stringResource(R.string.custom_alerts_state_paused)
    }
    val editDescription = stringResource(R.string.custom_alerts_edit_cd, ruleState, ruleDescription)
    val pauseDescription = stringResource(R.string.custom_alerts_pause_cd, ruleDescription)
    val resumeDescription = stringResource(R.string.custom_alerts_resume_cd, ruleDescription)
    val deleteDescription = stringResource(R.string.custom_alerts_delete_cd, ruleDescription)
    val onDescription = stringResource(R.string.common_on)
    val offDescription = stringResource(R.string.common_off)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (rule.enabled) NimbusBlueAccent.copy(alpha = 0.10f) else NimbusGlassTop.copy(alpha = 0.52f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(
                1.dp,
                if (rule.enabled) NimbusBlueAccent.copy(alpha = 0.22f) else NimbusCardBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable(
                onClick = onEdit,
                role = Role.Button,
            )
            .semantics(mergeDescendants = false) {
                contentDescription = editDescription
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AlertHintBadge(
                text = if (rule.enabled) {
                    stringResource(R.string.custom_alerts_status_active)
                } else {
                    stringResource(R.string.custom_alerts_status_paused)
                },
                tint = if (rule.enabled) NimbusBlueAccent else NimbusTextTertiary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ruleLabel,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (rule.enabled) NimbusTextPrimary else NimbusTextTertiary,
            )
            Text(
                text = metricSummary,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextTertiary,
            )
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics {
                contentDescription = if (rule.enabled) {
                    pauseDescription
                } else {
                    resumeDescription
                }
                stateDescription = if (rule.enabled) onDescription else offDescription
            },
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusError.copy(alpha = 0.12f))
                .clickable(
                    onClick = onDelete,
                    role = Role.Button,
                )
                .semantics { contentDescription = deleteDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = NimbusError.copy(alpha = 0.88f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatThreshold(rule: CustomAlertRule, settings: NimbusSettings): String {
    val display = convertForDisplay(rule.thresholdCanonical, rule.metric, settings)
    val label = displayUnitLabel(rule.metric, settings)
    val text = when (rule.metric.unit) {
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.CELSIUS,
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.KMH ->
            kotlin.math.round(display).toInt().toString()
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.MM,
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.UV,
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.HPA ->
            // Display in the default locale so it matches the input
            // convention (the editor accepts comma decimals). Stored values
            // stay canonical doubles — locale-invariant.
            String.format(Locale.getDefault(), "%.1f", display)
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.AQI ->
            kotlin.math.round(display).toInt().toString()
    }
    return "$text$label"
}

@Composable
private fun RuleEditor(
    initial: CustomAlertRule,
    isNew: Boolean,
    settings: NimbusSettings,
    onSave: (CustomAlertMetric, CustomAlertOperator, Double, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var metric by rememberSaveable { mutableStateOf(initial.metric) }
    var operator by rememberSaveable { mutableStateOf(initial.operator) }
    var thresholdText by rememberSaveable {
        mutableStateOf(
            formatThresholdInput(initial.thresholdCanonical, initial.metric, settings),
        )
    }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var previousMetric by rememberSaveable { mutableStateOf(initial.metric) }
    val parsedThreshold = parseThresholdInput(thresholdText)

    androidx.compose.runtime.LaunchedEffect(metric) {
        if (metric != previousMetric) {
            operator = defaultOperator(metric)
            thresholdText = defaultThresholdText(metric, settings)
            previousMetric = metric
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RuleEditorHeader(isNew = isNew)
        RuleMetricPicker(metric = metric, onMetricChange = { metric = it })
        RuleOperatorPicker(operator = operator, onOperatorChange = { operator = it })
        RuleThresholdInput(
            metric = metric,
            operator = operator,
            settings = settings,
            thresholdText = thresholdText,
            parsedThreshold = parsedThreshold,
            onThresholdTextChange = { thresholdText = it },
        )
        RuleEnabledToggle(enabled = enabled, onEnabledChange = { enabled = it })
        RuleEditorActions(
            parsedThreshold = parsedThreshold,
            thresholdText = thresholdText,
            metric = metric,
            operator = operator,
            enabled = enabled,
            onSave = onSave,
            onCancel = onCancel,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RuleEditorHeader(isNew: Boolean) {
    Text(
        text = if (isNew) {
            stringResource(R.string.custom_alerts_new_title)
        } else {
            stringResource(R.string.custom_alerts_edit_title)
        },
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = NimbusTextPrimary,
    )
    Text(
        text = stringResource(R.string.custom_alerts_editor_help),
        style = MaterialTheme.typography.bodySmall,
        color = NimbusTextSecondary,
    )
}

@Composable
private fun RuleMetricPicker(
    metric: CustomAlertMetric,
    onMetricChange: (CustomAlertMetric) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.custom_alerts_metric),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )
        SegmentSelector(
            options = CustomAlertMetric.entries,
            selected = metric,
            label = { stringResource(it.labelRes) },
            onSelect = onMetricChange,
        )
    }
}

@Composable
private fun RuleOperatorPicker(
    operator: CustomAlertOperator,
    onOperatorChange: (CustomAlertOperator) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.custom_alerts_direction),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )
        SegmentSelector(
            options = CustomAlertOperator.entries,
            selected = operator,
            label = { option ->
                stringResource(
                    R.string.custom_alerts_operator_chip,
                    option.symbol,
                    stringResource(option.labelRes),
                )
            },
            onSelect = onOperatorChange,
        )
    }
}

@Composable
private fun RuleThresholdInput(
    metric: CustomAlertMetric,
    operator: CustomAlertOperator,
    settings: NimbusSettings,
    thresholdText: String,
    parsedThreshold: Double?,
    onThresholdTextChange: (String) -> Unit,
) {
    val displayUnit = displayUnitLabel(metric, settings)
    val thresholdUnitLabel = displayUnit.trim().ifBlank { stringResource(R.string.custom_alerts_threshold_uv) }
    val thresholdFieldDescription = stringResource(
        R.string.custom_alerts_threshold_field_cd,
        stringResource(metric.labelRes),
        thresholdUnitLabel,
    )
    val invalidThresholdState = stringResource(R.string.custom_alerts_threshold_invalid_state)
    val showThresholdError = shouldShowThresholdError(metric, thresholdText, parsedThreshold)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.custom_alerts_threshold_label, thresholdUnitLabel),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )
        Text(
            text = stringResource(metric.summaryRes),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextTertiary,
        )
        BasicTextField(
            value = thresholdText,
            // Map ',' -> '.' BEFORE filtering: locales typing "5,5" must become
            // "5.5", not have the comma stripped into "55".
            onValueChange = { onThresholdTextChange(it.replace(',', '.').filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }) },
            textStyle = TextStyle(color = NimbusTextPrimary, fontSize = 20.sp),
            cursorBrush = SolidColor(NimbusBlueAccent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop.copy(alpha = 0.44f),
                            NimbusNavyDark,
                        ),
                    ),
                )
                .border(
                    1.dp,
                    if (showThresholdError) {
                        NimbusError.copy(alpha = 0.42f)
                    } else {
                        NimbusCardBorder
                    },
                    RoundedCornerShape(8.dp),
                )
                .semantics {
                    contentDescription = thresholdFieldDescription
                    if (showThresholdError) {
                        stateDescription = invalidThresholdState
                    }
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
        RuleThresholdFeedback(
            metric = metric,
            operator = operator,
            thresholdText = thresholdText,
            displayUnit = displayUnit,
            parsedThreshold = parsedThreshold,
        )
    }
}

@Composable
private fun RuleThresholdFeedback(
    metric: CustomAlertMetric,
    operator: CustomAlertOperator,
    thresholdText: String,
    displayUnit: String,
    parsedThreshold: Double?,
) {
    when {
        isThresholdInputInProgress(thresholdText) -> Unit
        parsedThreshold == null || parsedThreshold.isNaN() -> Text(
            text = stringResource(R.string.custom_alerts_invalid_threshold),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusError,
        )
        !metricAllowsNegativeThreshold(metric) && parsedThreshold < 0.0 -> Text(
            text = stringResource(R.string.custom_alerts_negative_threshold),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusError,
        )
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            AlertHintBadge(
                text = stringResource(
                    R.string.custom_alerts_trigger_preview,
                    stringResource(metric.labelRes).lowercase(locale),
                    stringResource(operator.labelRes),
                    thresholdText,
                    displayUnit,
                ),
            )
        }
    }
}

/**
 * Whether a metric's threshold may be negative. Temperatures can; precipitation,
 * UV index, and wind gusts can't — a negative value would make those rules
 * always-true (`>`) or never-true (`<`).
 */
private fun metricAllowsNegativeThreshold(metric: CustomAlertMetric): Boolean = when (metric) {
    CustomAlertMetric.TEMP_HIGH_TODAY,
    CustomAlertMetric.TEMP_LOW_TONIGHT,
    CustomAlertMetric.DEW_POINT_NOW,
    CustomAlertMetric.FEELS_LIKE_NOW,
    -> true
    CustomAlertMetric.WIND_GUST_NEXT_12H,
    CustomAlertMetric.PRECIP_SUM_NEXT_24H,
    CustomAlertMetric.UV_INDEX_MAX_TODAY,
    CustomAlertMetric.SNOWFALL_SUM_NEXT_24H,
    CustomAlertMetric.PRESSURE_NOW,
    CustomAlertMetric.AQI_NOW,
    -> false
}

/** Save-gate: a threshold must parse, not be NaN, and not be negative for non-negative metrics. */
private fun isThresholdValid(metric: CustomAlertMetric, parsedThreshold: Double?): Boolean =
    parsedThreshold != null &&
        !parsedThreshold.isNaN() &&
        (metricAllowsNegativeThreshold(metric) || parsedThreshold >= 0.0)

internal fun isThresholdInputInProgress(thresholdText: String): Boolean {
    val trimmed = thresholdText.trim()
    return trimmed.isEmpty() ||
        trimmed == "-" ||
        trimmed == "." ||
        trimmed == "-." ||
        trimmed.endsWith(".")
}

private fun shouldShowThresholdError(
    metric: CustomAlertMetric,
    thresholdText: String,
    parsedThreshold: Double?,
): Boolean {
    return !isThresholdInputInProgress(thresholdText) &&
        !isThresholdValid(metric, parsedThreshold)
}

@Composable
private fun RuleEnabledToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val enabledStateDescription = if (enabled) {
        stringResource(R.string.common_on)
    } else {
        stringResource(R.string.common_off)
    }
    val enabledContentDescription = stringResource(
        R.string.custom_alerts_enabled_cd,
        if (enabled) {
            stringResource(R.string.custom_alerts_enabled_notifications_on)
        } else {
            stringResource(R.string.custom_alerts_enabled_notifications_paused)
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (enabled) NimbusBlueAccent.copy(alpha = 0.12f) else NimbusGlassTop.copy(alpha = 0.46f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(
                1.dp,
                if (enabled) NimbusBlueAccent.copy(alpha = 0.26f) else NimbusCardBorder,
                RoundedCornerShape(10.dp),
            )
            .toggleable(
                value = enabled,
                onValueChange = onEnabledChange,
                role = Role.Switch,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = enabledContentDescription
                stateDescription = enabledStateDescription
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.custom_alerts_enabled),
                color = NimbusTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) {
                    stringResource(R.string.custom_alerts_enabled_desc_on)
                } else {
                    stringResource(R.string.custom_alerts_enabled_desc_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun RuleEditorActions(
    parsedThreshold: Double?,
    thresholdText: String,
    metric: CustomAlertMetric,
    operator: CustomAlertOperator,
    enabled: Boolean,
    onSave: (CustomAlertMetric, CustomAlertOperator, Double, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val thresholdValid = !isThresholdInputInProgress(thresholdText) &&
        isThresholdValid(metric, parsedThreshold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, NimbusCardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = NimbusTextSecondary,
            ),
        ) {
            Text(stringResource(R.string.common_cancel), maxLines = 1)
        }
        Button(
            enabled = thresholdValid,
            modifier = Modifier
                .weight(1.25f)
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = NimbusCardBorder.copy(alpha = 0.72f),
                disabledContentColor = NimbusTextTertiary,
            ),
            onClick = {
                parsedThreshold?.takeIf { thresholdValid }?.let { parsed ->
                    onSave(metric, operator, parsed, enabled)
                }
            },
        ) {
            Text(
                stringResource(R.string.common_save),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun <T> SegmentSelector(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    NimbusScrollableSegmentRow {
        options.forEach { option ->
            val isSelected = option == selected
            val optionLabel = label(option)
            NimbusSelectableSegment(
                label = optionLabel,
                selected = isSelected,
                onClick = { onSelect(option) },
                role = Role.RadioButton,
                showIndicator = false,
                compact = true,
            )
        }
    }
}

@Composable
private fun CustomAlertsIntroCard(
    ruleCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.72f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusBlueAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = NimbusBlueAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.custom_alerts_rule_center),
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pluralStringResource(R.plurals.custom_alerts_rule_count, ruleCount, ruleCount),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }
    }
}

@Composable
private fun AlertHintBadge(
    text: String,
    tint: Color = NimbusBlueAccent,
) {
    NimbusStatusBadge(
        text = text,
        tint = tint,
        emphasized = tint == NimbusBlueAccent,
    )
}

private fun defaultThresholdText(
    metric: CustomAlertMetric,
    settings: NimbusSettings,
): String = formatThresholdInput(defaultThresholdCanonical(metric), metric, settings)

private fun formatThresholdInput(
    canonicalValue: Double,
    metric: CustomAlertMetric,
    settings: NimbusSettings,
): String {
    val displayValue = convertForDisplay(canonicalValue, metric, settings)
    return when (metric.unit) {
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.CELSIUS,
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.KMH ->
            kotlin.math.round(displayValue).toInt().toString()
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.MM,
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.UV,
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.HPA ->
            // Default locale so the pre-filled value matches what comma-decimal
            // locales type; parseThresholdInput normalizes either separator.
            String.format(Locale.getDefault(), "%.1f", displayValue)
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.AQI ->
            kotlin.math.round(displayValue).toInt().toString()
    }
}

/**
 * Parse editor text accepting either decimal separator: user edits are
 * normalized to '.' as they type, but the pre-filled value is formatted in
 * the default locale and may carry ','.
 */
internal fun parseThresholdInput(thresholdText: String): Double? =
    thresholdText.replace(',', '.').toDoubleOrNull()
