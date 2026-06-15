package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.ReportCondition
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusError
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyMid
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.labelRes

/**
 * Bottom sheet for submitting a community weather report.
 * Shows a grid of weather condition segments and an optional note field.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSubmitSheet(
    isSubmitting: Boolean,
    submitResult: String?, // null = idle, "success" = done, else error message
    onSubmit: (ReportCondition, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCondition by rememberSaveable { mutableStateOf<ReportCondition?>(null) }
    var noteText by rememberSaveable { mutableStateOf("") }
    val bottomSheetHandleDescription = stringResource(R.string.common_bottom_sheet_handle)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NimbusNavyDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clearAndSetSemantics {
                        contentDescription = bottomSheetHandleDescription
                    },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.report_sheet_title),
                color = NimbusTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.report_sheet_body),
                color = NimbusTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Condition selection grid
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportCondition.entries.forEach { condition ->
                    val isSelected = selectedCondition == condition
                    ConditionChip(
                        condition = condition,
                        isSelected = isSelected,
                        onClick = { selectedCondition = condition },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Optional note
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 100) noteText = it },
                label = { Text(stringResource(R.string.report_note_label), color = NimbusTextTertiary) },
                placeholder = { Text(stringResource(R.string.report_note_placeholder), color = NimbusTextTertiary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NimbusTextPrimary,
                    unfocusedTextColor = NimbusTextPrimary,
                    cursorColor = NimbusBlueAccent,
                    focusedBorderColor = NimbusBlueAccent,
                    unfocusedBorderColor = NimbusCardBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        text = stringResource(R.string.report_note_count, noteText.length, 100),
                        color = NimbusTextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Submit button
            Button(
                onClick = {
                    selectedCondition?.let { onSubmit(it, noteText.trim()) }
                },
                enabled = selectedCondition != null && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NimbusBlueAccent,
                    contentColor = NimbusTextPrimary,
                    disabledContainerColor = NimbusNavyMid,
                    disabledContentColor = NimbusTextTertiary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isSubmitting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = NimbusTextPrimary,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.report_submit),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Text(
                        text = if (selectedCondition == null) {
                            stringResource(R.string.report_choose_condition)
                        } else {
                            stringResource(R.string.report_submit)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Feedback
            AnimatedVisibility(
                visible = submitResult != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val isSuccess = submitResult == "success"
                val feedbackMessage = if (isSuccess) {
                    stringResource(R.string.report_success)
                } else {
                    submitResult ?: ""
                }
                ReportFeedbackCard(
                    message = feedbackMessage,
                    isSuccess = isSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConditionChip(
    condition: ReportCondition,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val conditionLabel = stringResource(condition.labelRes)
    val conditionContentDescription = stringResource(R.string.report_condition_cd, conditionLabel)
    val selectedStateDescription = stringResource(
        if (isSelected) R.string.common_selected else R.string.common_not_selected,
    )
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (isSelected) NimbusBlueAccent.copy(alpha = 0.3f) else NimbusCardBg
    val borderColor = if (isSelected) NimbusBlueAccent else NimbusCardBorder

    Box(
        modifier = Modifier
            .widthIn(min = 82.dp)
            .heightIn(min = 66.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = conditionContentDescription
                stateDescription = selectedStateDescription
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = condition.emoji,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = conditionLabel,
                color = if (isSelected) NimbusTextPrimary else NimbusTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReportFeedbackCard(
    message: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isSuccess) NimbusSuccess else NimbusError
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            color = if (isSuccess) NimbusTextPrimary else NimbusError,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}
