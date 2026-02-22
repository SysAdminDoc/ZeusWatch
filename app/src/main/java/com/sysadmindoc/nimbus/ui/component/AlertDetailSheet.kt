package com.sysadmindoc.nimbus.ui.component

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
            // Custom drag handle bar
            Spacer(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .padding(0.dp)
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
            // Header: icon + event + severity
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = alert.severity.color,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = alert.event,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = alert.severity.color,
                    )
                    Text(
                        text = "${alert.severity.label} severity \u2022 ${alert.urgency.label} urgency",
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Headline
            Text(
                text = alert.headline,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextPrimary,
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = NimbusCardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Metadata row
            MetadataItem("Issued by", alert.senderName)
            MetadataItem("Areas", alert.areaDescription)
            alert.effective?.let { MetadataItem("Effective", formatAlertTime(it)) }
            alert.expires?.let { MetadataItem("Expires", formatAlertTime(it)) }
            alert.response?.let { MetadataItem("Action", it) }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = NimbusCardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Description
            if (alert.description.isNotBlank()) {
                Text(
                    text = "Description",
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

            // Instructions
            if (!alert.instruction.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Instructions",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = alert.severity.color,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alert.instruction.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextPrimary,
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
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatAlertTime(isoString: String): String = try {
    val odt = OffsetDateTime.parse(isoString)
    odt.format(DateTimeFormatter.ofPattern("EEE MMM d, h:mm a z", Locale.US))
} catch (_: Exception) {
    isoString
}
