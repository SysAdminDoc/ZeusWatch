package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.util.PetSafetyAlert

@Composable
fun PetSafetyCard(
    alerts: List<PetSafetyAlert>,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    WeatherCard(title = "Pet Safety", modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            alerts.forEach { alert ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Pets,
                        contentDescription = "Pet safety alert",
                        modifier = Modifier.size(18.dp),
                        tint = alert.severity.color,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = alert.severity.color,
                        )
                        if (alert.detail.isNotBlank()) {
                            Text(
                                text = alert.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = NimbusTextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
