package com.sysadmindoc.nimbus.ui.component

import android.content.res.Resources
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.sysadmindoc.nimbus.util.isReducedMotionEnabled
import com.sysadmindoc.nimbus.util.labelRes
import com.sysadmindoc.nimbus.util.parseAlertInstant
import java.time.Duration
import java.time.Instant
import java.util.Locale

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
    val resources = LocalResources.current

    // Haptic feedback for severe+ alerts on first composition
    if (settings.hapticFeedbackForAlerts) {
        val alertSetKey = remember(alerts) { alerts.map { it.id }.sorted().joinToString("|") }
        val highestSeverityAlert = remember(alerts) { alerts.minByOrNull { it.severity.sortOrder } }
        androidx.compose.runtime.LaunchedEffect(alertSetKey) {
            highestSeverityAlert?.let {
                com.sysadmindoc.nimbus.util.HapticHelper.vibrateForAlert(context, it.severity)
            }
        }
    }

    val alertDescription = alerts.joinToString(". ") { alert ->
        resources.getString(
            R.string.alert_banner_item_cd,
            resources.getString(alert.severity.labelRes),
            listOfNotNull(alert.event, alert.coverageLabel(resources)).joinToString(", "),
        )
    }
    val bannerContentDescription = resources.getString(R.string.alert_banner_list_cd, alertDescription)

    val groups = remember(alerts) { groupAlertsForBanner(alerts) }
    var showAll by rememberSaveable(alerts.size) { mutableStateOf(false) }
    val hiddenCount = (groups.size - MAX_COLLAPSED_ALERT_GROUPS).coerceAtLeast(0)
    val visibleGroups = if (showAll || hiddenCount == 0) groups else groups.take(MAX_COLLAPSED_ALERT_GROUPS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = bannerContentDescription
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleGroups.forEach { group ->
            AlertBannerItem(
                group = group,
                onClick = { onAlertClick(group.primary) },
            )
        }
        if (hiddenCount > 0 && !showAll) {
            AlertBannerMoreRow(
                hiddenCount = hiddenCount,
                onClick = { showAll = true },
            )
        }
    }
}

/**
 * A row of alerts that say the same thing about different places.
 *
 * A national feed answers one query with one alert per administrative area, so
 * "Heavy Rain / Severe" arrives forty times with forty area strings. Collapsing
 * on event plus severity turns that back into one row that names the areas,
 * which is the shape a reader can actually use.
 */
internal data class AlertBannerGroup(
    val primary: WeatherAlert,
    val areas: List<String>,
    val collapsedCount: Int,
)

/** How many groups the banner shows before it offers to expand. */
internal const val MAX_COLLAPSED_ALERT_GROUPS = 5

/**
 * Collapse [alerts] on event plus severity, preserving the caller's order.
 *
 * The repository already sorts by severity then urgency, so the first alert in
 * each group is the one worth leading with and the resulting group order is
 * still severity-first.
 */
internal fun groupAlertsForBanner(alerts: List<WeatherAlert>): List<AlertBannerGroup> {
    val grouped = LinkedHashMap<Pair<String, AlertSeverity>, MutableList<WeatherAlert>>()
    alerts.forEach { alert ->
        val key = alert.event.trim().lowercase(Locale.ROOT) to alert.severity
        grouped.getOrPut(key) { mutableListOf() }.add(alert)
    }
    return grouped.values.map { members ->
        AlertBannerGroup(
            primary = members.first(),
            areas = members
                .map { it.areaDescription.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            collapsedCount = members.size,
        )
    }
}

@Composable
private fun AlertBannerMoreRow(
    hiddenCount: Int,
    onClick: () -> Unit,
) {
    val label = pluralStringResource(R.plurals.alert_banner_show_more, hiddenCount, hiddenCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111833))
            .border(1.dp, NimbusTextTertiary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextSecondary,
        )
    }
}

@Composable
private fun AlertBannerItem(
    group: AlertBannerGroup,
    onClick: () -> Unit,
) {
    val alert = group.primary
    val resources = LocalResources.current
    val shape = RoundedCornerShape(12.dp)
    val severityColor = alert.severity.color
    val bgColor = severityColor.copy(alpha = 0.12f)
    val severityLabel = stringResource(alert.severity.labelRes)
    val urgencyLabel = stringResource(alert.urgency.labelRes)
    val coverageText = alert.coverageLabel(resources)

    // Pulse border for extreme alerts; honor reduced motion with a static
    // emphasized border instead.
    val reducedMotion = isReducedMotionEnabled()
    val borderAlpha = when {
        alert.severity == AlertSeverity.EXTREME && reducedMotion -> 1f
        alert.severity == AlertSeverity.EXTREME -> {
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
        }
        else -> 0.6f
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
                    contentDescription = stringResource(R.string.alert_banner_icon_cd, alert.event),
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
                    alert.expires?.let { formatExpiresIn(resources, it) }?.let { expiresIn ->
                        AlertMetaBadge(
                            text = expiresIn,
                            tint = NimbusTextTertiary,
                        )
                    }
                }

                coverageText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = alert.headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (group.areas.isNotEmpty()) {
                    Text(
                        text = group.areas.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = NimbusTextTertiary,
                        maxLines = if (group.collapsedCount > 1) 3 else 1,
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

private fun WeatherAlert.coverageLabel(resources: Resources): String? {
    return when (coversRequestedLocation) {
        true -> resources.getString(R.string.alert_coverage_covers_current)
        false -> resources.getString(R.string.alert_coverage_nearby_polygon)
        null -> null
    }
}

private fun formatExpiresIn(resources: Resources, isoString: String): String? {
    val expires = parseAlertInstant(isoString) ?: return null
    val now = Instant.now()
    if (expires.isBefore(now)) return resources.getString(R.string.alert_time_expired)
    val dur = Duration.between(now, expires)
    val hours = dur.toHours()
    val minutes = dur.toMinutes() % 60
    return when {
        hours >= 24 -> resources.getString(R.string.alert_time_days_hours_left, hours / 24, hours % 24)
        hours >= 1 -> resources.getString(R.string.alert_time_hours_minutes_left, hours, minutes)
        minutes > 0 -> resources.getString(R.string.alert_time_minutes_left, minutes)
        else -> null
    }
}
