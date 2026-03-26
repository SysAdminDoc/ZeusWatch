package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.util.HealthAlert

/**
 * Card showing health-related weather alerts (migraine triggers, respiratory, joint pain).
 */
@Composable
fun HealthAlertCard(
    alerts: List<HealthAlert>,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    WeatherCard(
        title = "Health Alerts",
        modifier = modifier,
    ) {
        alerts.forEachIndexed { idx, alert ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(alert.severity.color.copy(alpha = 0.1f))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.HealthAndSafety,
                    contentDescription = null,
                    tint = alert.severity.color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${alert.type.label} \u2022 ${alert.severity.label}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = alert.severity.color,
                    )
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextPrimary,
                    )
                    if (alert.detail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = alert.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimbusTextSecondary,
                        )
                    }
                }
            }
            if (idx < alerts.lastIndex) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
