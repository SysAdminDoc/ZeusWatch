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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.util.DrivingAlert

/**
 * Card showing driving condition alerts (ice, fog, hydroplaning, wind, etc).
 */
@Composable
fun DrivingAlertCard(
    alerts: List<DrivingAlert>,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    WeatherCard(
        titleRes = R.string.card_type_driving_conditions,
        modifier = modifier,
    ) {
        alerts.forEachIndexed { idx, alert ->
            val typeLabel = stringResource(alert.type.labelRes)
            val severityLabel = stringResource(alert.severity.labelRes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(alert.severity.color.copy(alpha = 0.1f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = stringResource(
                        R.string.driving_alert_content_description,
                        typeLabel,
                    ),
                    tint = alert.severity.color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$typeLabel \u2022 $severityLabel",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = alert.severity.color,
                    )
                    Text(
                        text = stringResource(alert.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextPrimary,
                    )
                }
            }
            if (idx < alerts.lastIndex) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
