package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Bottom sheet for submitting a community weather report.
 * Shows a grid of weather condition chips and an optional note field.
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
    var selectedCondition by remember { mutableStateOf<ReportCondition?>(null) }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NimbusNavyDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NimbusTextTertiary) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Report Current Conditions",
                color = NimbusTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Help others by sharing what you see outside",
                color = NimbusTextSecondary,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Condition selection grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
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
                label = { Text("Add a note (optional)", color = NimbusTextTertiary) },
                placeholder = { Text("e.g. Light drizzle starting", color = NimbusTextTertiary) },
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
                        text = "${noteText.length}/100",
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NimbusTextPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Submit Report", fontWeight = FontWeight.SemiBold)
                }
            }

            // Feedback
            AnimatedVisibility(
                visible = submitResult != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val isSuccess = submitResult == "success"
                Text(
                    text = if (isSuccess) "Report submitted! Thanks for helping your community." else (submitResult ?: ""),
                    color = if (isSuccess) NimbusSuccess else NimbusError,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
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
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (isSelected) NimbusBlueAccent.copy(alpha = 0.3f) else NimbusCardBg
    val borderColor = if (isSelected) NimbusBlueAccent else NimbusCardBorder

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = condition.emoji,
                fontSize = 22.sp,
            )
            Text(
                text = condition.label,
                color = if (isSelected) NimbusTextPrimary else NimbusTextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
