package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary

/**
 * Alert banner displayed at the top of the main screen when active alerts exist.
 * Color-coded by severity. Tapping opens the alert detail sheet.
 * Extreme severity alerts pulse the border.
 */
@Composable
fun AlertBanner(
    alerts: List<WeatherAlert>,
    onAlertClick: (WeatherAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        alerts.forEach { alert ->
            AlertBannerItem(alert = alert, onClick = { onAlertClick(alert) })
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AlertBannerItem(
    alert: WeatherAlert,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val severityColor = alert.severity.color
    val bgColor = severityColor.copy(alpha = 0.15f)

    // Pulse border for extreme alerts
    val borderAlpha = if (alert.severity == AlertSeverity.EXTREME) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        pulse
    } else {
        0.6f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(1.dp, severityColor.copy(alpha = borderAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = severityColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alert.event,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = severityColor,
            )
            Text(
                text = alert.headline,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
