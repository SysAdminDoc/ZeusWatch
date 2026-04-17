package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.sysadmindoc.nimbus.wear.data.WearAlertEntry

private val TextPrimary = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFB0B8CC)
private val TextTertiary = Color(0xFF7A839E)

private val SeverityExtreme = Color(0xFFD32F2F)
private val SeveritySevere = Color(0xFFFF5722)
private val SeverityModerate = Color(0xFFFF9800)
private val SeverityMinor = Color(0xFFFFEB3B)
private val SeverityUnknown = Color(0xFF9E9E9E)

@Composable
fun AlertsScreen(
    alerts: List<WearAlertEntry>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()

    if (alerts.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("\u2705", fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "No active alerts",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            ListHeader {
                Text(
                    "\u26A0\uFE0F Alerts (${alerts.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
        }
        items(alerts) { alert ->
            AlertRow(alert)
        }
    }
}

@Composable
private fun AlertRow(alert: WearAlertEntry) {
    val severityColor = severityColor(alert.severity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Severity dot
            Spacer(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(severityColor)
            )
            Spacer(Modifier.width(6.dp))
            // Event name
            Text(
                text = alert.event,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = severityColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Headline
        if (alert.headline.isNotBlank()) {
            Text(
                text = alert.headline,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            )
        }
        // Expires
        if (alert.expires.isNotBlank()) {
            Text(
                text = "Expires: ${formatExpiry(alert.expires)}",
                fontSize = 10.sp,
                color = TextTertiary,
                modifier = Modifier.padding(start = 14.dp, top = 1.dp),
            )
        }
    }
}

private fun severityColor(severity: String): Color = when (severity.lowercase(java.util.Locale.ROOT)) {
    "extreme" -> SeverityExtreme
    "severe" -> SeveritySevere
    "moderate" -> SeverityModerate
    "minor" -> SeverityMinor
    else -> SeverityUnknown
}

private fun formatExpiry(isoExpires: String): String {
    // Show just the time portion for brevity on the watch
    val timePart = isoExpires.substringAfter("T").substringBefore("+").substringBefore("Z")
    if (timePart.isBlank() || timePart == isoExpires) return isoExpires
    val hourMin = timePart.substringBeforeLast(":")
    val hour = hourMin.substringBefore(":").toIntOrNull() ?: return hourMin
    val min = hourMin.substringAfter(":").padStart(2, '0')
    return when {
        hour == 0 -> "12:${min}AM"
        hour < 12 -> "${hour}:${min}AM"
        hour == 12 -> "12:${min}PM"
        else -> "${hour - 12}:${min}PM"
    }
}
