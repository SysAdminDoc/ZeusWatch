package com.sysadmindoc.nimbus.ui.screen.customalerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.convertForDisplay
import com.sysadmindoc.nimbus.util.displayUnitLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAlertsScreen(
    onBack: () -> Unit,
    viewModel: CustomAlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditorState?>(null) }

    PredictiveBackScaffold(onBack = onBack) {
        Scaffold(
            containerColor = NimbusNavyDark,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NimbusTextPrimary,
                        )
                    }
                    Text(
                        "Custom Alerts",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = NimbusTextPrimary,
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { editing = EditorState(existing = null, draft = viewModel.defaultRule()) },
                    containerColor = NimbusBlueAccent,
                    contentColor = NimbusTextPrimary,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add custom alert")
                }
            },
        ) { innerPadding ->
            if (state.rules.isEmpty()) {
                EmptyState(modifier = Modifier.padding(innerPadding).fillMaxSize())
            } else {
                RuleList(
                    rules = state.rules,
                    settings = state.settings,
                    onToggle = { viewModel.toggleRule(it) },
                    onEdit = { editing = EditorState(existing = it, draft = it) },
                    onDelete = { viewModel.deleteRule(it) },
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
                    viewModel.upsertRule(
                        existing = ed.existing,
                        metric = metric,
                        operator = op,
                        thresholdInDisplayUnits = threshold,
                        enabled = enabled,
                    )
                    editing = null
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = NimbusTextTertiary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "No custom alerts yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Add a rule to get notified when tomorrow's high goes above 32°C, the UV peak hits 9, or the next 24 hours drops more than 20 mm of rain.",
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
            textAlign = TextAlign.Center,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NimbusCardBg)
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${rule.metric.label} ${rule.operator.symbol} ${formatThreshold(rule, settings)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (rule.enabled) NimbusTextPrimary else NimbusTextTertiary,
            )
            Text(
                text = rule.metric.summary,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextTertiary,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete rule",
                tint = NimbusTextTertiary,
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
        com.sysadmindoc.nimbus.data.model.CustomAlertUnit.UV ->
            String.format(java.util.Locale.US, "%.1f", display)
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
    var metric by remember { mutableStateOf(initial.metric) }
    var operator by remember { mutableStateOf(initial.operator) }
    var thresholdText by remember(metric) {
        val displayValue = convertForDisplay(initial.thresholdCanonical, metric, settings)
        val defaultText = when (metric.unit) {
            com.sysadmindoc.nimbus.data.model.CustomAlertUnit.CELSIUS,
            com.sysadmindoc.nimbus.data.model.CustomAlertUnit.KMH ->
                kotlin.math.round(displayValue).toInt().toString()
            com.sysadmindoc.nimbus.data.model.CustomAlertUnit.MM,
            com.sysadmindoc.nimbus.data.model.CustomAlertUnit.UV ->
                String.format(java.util.Locale.US, "%.1f", displayValue)
        }
        mutableStateOf(defaultText)
    }
    var enabled by remember { mutableStateOf(initial.enabled) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (isNew) "New custom alert" else "Edit custom alert",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextPrimary,
        )

        // Metric picker
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Metric", style = MaterialTheme.typography.labelMedium, color = NimbusTextSecondary)
            ChipSelector(
                options = CustomAlertMetric.entries,
                selected = metric,
                label = { it.label },
                onSelect = { metric = it },
            )
        }

        // Operator picker
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Direction", style = MaterialTheme.typography.labelMedium, color = NimbusTextSecondary)
            ChipSelector(
                options = CustomAlertOperator.entries,
                selected = operator,
                label = { "${it.symbol} ${it.label}" },
                onSelect = { operator = it },
            )
        }

        // Threshold input
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Threshold (${displayUnitLabel(metric, settings).trim().ifBlank { "UV" }})",
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )
            BasicTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                textStyle = TextStyle(color = NimbusTextPrimary, fontSize = 20.sp),
                cursorBrush = SolidColor(NimbusBlueAccent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NimbusNavyDark)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }

        // Enabled toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enabled", modifier = Modifier.weight(1f), color = NimbusTextPrimary)
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = NimbusTextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull() ?: return@TextButton
                    onSave(metric, operator, parsed, enabled)
                },
            ) {
                Text("Save", color = NimbusBlueAccent, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun <T> ChipSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    // Simple flow-wrapping row via a LazyColumn is overkill; 2 enums with a
    // handful of entries fit on one or two lines of horizontally-scrolling chips.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) NimbusBlueAccent else NimbusNavyDark)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else NimbusTextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

