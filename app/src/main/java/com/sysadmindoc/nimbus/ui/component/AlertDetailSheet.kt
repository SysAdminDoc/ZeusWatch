package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusSurface
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Modal bottom sheet showing full alert details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailSheet(
    alert: WeatherAlert,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NimbusSurface,
        scrimColor = NimbusNavyDark.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "${alert.severity.name} weather alert",
                    tint = alert.severity.color,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = alert.event,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = alert.severity.color,
                    )
                    Text(
                        text = alert.headline,
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                        maxLines = 2,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AlertSheetPill(
                    text = alert.severity.label,
                    background = alert.severity.color.copy(alpha = 0.16f),
                    textColor = alert.severity.color,
                )
                Spacer(modifier = Modifier.width(8.dp))
                AlertSheetPill(
                    text = "${alert.urgency.label} urgency",
                    background = Color.White.copy(alpha = 0.06f),
                    textColor = NimbusTextSecondary,
                )
                alert.response?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    AlertSheetPill(
                        text = it,
                        background = Color.White.copy(alpha = 0.06f),
                        textColor = NimbusTextTertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "Alert Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = NimbusTextPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem("Issued by", alert.senderName)
                MetadataItem("Areas", alert.areaDescription)
                alert.effective?.let { MetadataItem("Effective", formatAlertTime(it)) }
                alert.expires?.let { MetadataItem("Expires", formatAlertTime(it)) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (alert.description.isNotBlank()) {
                Text(
                    text = "What this means",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alert.description.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                )
            }

            if (!alert.instruction.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                InlineNoticeCard(
                    title = "Recommended action",
                    message = alert.instruction.trim(),
                    icon = Icons.Filled.Warning,
                    tint = alert.severity.color,
                )
            }
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlertSheetPill(
    text: String,
    background: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

private fun formatAlertTime(isoString: String): String = try {
    val odt = OffsetDateTime.parse(isoString)
    odt.format(DateTimeFormatter.ofPattern("EEE MMM d, h:mm a z", Locale.getDefault()))
} catch (_: Exception) {
    isoString
}
