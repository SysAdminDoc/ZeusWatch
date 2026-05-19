package com.sysadmindoc.nimbus.ui.component

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.labelRes
import java.time.Duration
import java.time.OffsetDateTime

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

    val settings = LocalUnitSettings.current
    val context = LocalContext.current

    // Haptic feedback for severe+ alerts on first composition
    if (settings.hapticFeedbackForAlerts) {
        val topAlert = alerts.firstOrNull()
        androidx.compose.runtime.LaunchedEffect(topAlert?.id) {
            topAlert?.let {
                com.sysadmindoc.nimbus.util.HapticHelper.vibrateForAlert(context, it.severity)
            }
        }
    }

    val alertDescription = alerts.joinToString(". ") { alert ->
        context.getString(
            R.string.alert_banner_item_cd,
            context.getString(alert.severity.labelRes),
            alert.event,
        )
    }
    val bannerContentDescription = context.getString(R.string.alert_banner_list_cd, alertDescription)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = bannerContentDescription
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        alerts.forEach { alert ->
            AlertBannerItem(alert = alert, onClick = { onAlertClick(alert) })
        }
    }
}

@Composable
private fun AlertBannerItem(
    alert: WeatherAlert,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val severityColor = alert.severity.color
    val bgColor = severityColor.copy(alpha = 0.12f)
    val severityLabel = context.getString(alert.severity.labelRes)
    val urgencyLabel = context.getString(alert.urgency.labelRes)

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        Color(0xFF111833),
                    ),
                ),
            )
            .border(1.dp, severityColor.copy(alpha = borderAlpha), shape)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(severityColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = context.getString(R.string.alert_banner_icon_cd, alert.event),
                    tint = severityColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = alert.event,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = severityColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertMetaBadge(
                        text = severityLabel,
                        tint = severityColor,
                        emphasized = true,
                    )
                    AlertMetaBadge(
                        text = urgencyLabel,
                        tint = NimbusTextSecondary,
                    )
                    alert.expires?.let { formatExpiresIn(context, it) }?.let { expiresIn ->
                        AlertMetaBadge(
                            text = expiresIn,
                            tint = NimbusTextTertiary,
                        )
                    }
                }

                Text(
                    text = alert.headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (alert.areaDescription.isNotBlank()) {
                    Text(
                        text = alert.areaDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertMetaBadge(
    text: String,
    tint: Color,
    emphasized: Boolean = false,
) {
    NimbusStatusBadge(
        text = text,
        tint = tint,
        emphasized = emphasized,
        maxLines = 1,
    )
}

private fun formatExpiresIn(context: Context, isoString: String): String? {
    return try {
        val expires = OffsetDateTime.parse(isoString)
        val now = OffsetDateTime.now()
        if (expires.isBefore(now)) return context.getString(R.string.alert_time_expired)
        val dur = Duration.between(now, expires)
        val hours = dur.toHours()
        val minutes = dur.toMinutes() % 60
        when {
            hours >= 24 -> context.getString(R.string.alert_time_days_hours_left, hours / 24, hours % 24)
            hours >= 1 -> context.getString(R.string.alert_time_hours_minutes_left, hours, minutes)
            minutes > 0 -> context.getString(R.string.alert_time_minutes_left, minutes)
            else -> null
        }
    } catch (_: Exception) { null }
}
