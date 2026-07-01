package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.DrivingRouteRiskLevel
import com.sysadmindoc.nimbus.data.repository.DrivingRouteWaypoint
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyMid
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteWeatherPlannerSheet(
    state: RoutePlannerUiState,
    settings: NimbusSettings,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onDepartureOffsetChange: (Int) -> Unit,
    onPlanRoute: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.isSheetOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NimbusNavyDark,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.route_planner_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = NimbusTextPrimary,
            )

            RoutePlannerTextField(
                value = state.originQuery,
                onValueChange = onOriginChange,
                label = stringResource(R.string.route_planner_origin_label),
                placeholder = stringResource(R.string.route_planner_origin_placeholder),
            )
            RoutePlannerTextField(
                value = state.destinationQuery,
                onValueChange = onDestinationChange,
                label = stringResource(R.string.route_planner_destination_label),
                placeholder = stringResource(R.string.route_planner_destination_placeholder),
            )

            RouteDepartureSelector(
                selectedMinutes = state.departureOffsetMinutes,
                onSelected = onDepartureOffsetChange,
            )

            state.error?.let { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    color = NimbusWarning,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onPlanRoute,
                enabled = !state.isPlanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NimbusBlueAccent,
                    contentColor = NimbusTextPrimary,
                    disabledContainerColor = NimbusNavyMid,
                    disabledContentColor = NimbusTextTertiary,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (state.isPlanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NimbusTextPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (state.isPlanning) {
                        stringResource(R.string.route_planner_planning)
                    } else {
                        stringResource(R.string.route_planner_plan)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            state.plan?.let { plan ->
                RoutePlanSummary(
                    risk = plan.risk,
                    distanceText = formatRouteDistance(plan.distanceKm, settings),
                    durationText = formatRouteDuration(plan.estimatedDurationMinutes),
                    waypointCount = plan.waypoints.size,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.route_planner_waypoints),
                        style = MaterialTheme.typography.labelLarge,
                        color = NimbusTextSecondary,
                    )
                    plan.waypoints.forEach { waypoint ->
                        RouteWaypointRow(waypoint = waypoint, settings = settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePlannerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(160)) },
        label = { Text(label, color = NimbusTextTertiary) },
        placeholder = { Text(placeholder, color = NimbusTextTertiary) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NimbusTextPrimary,
            unfocusedTextColor = NimbusTextPrimary,
            cursorColor = NimbusBlueAccent,
            focusedBorderColor = NimbusBlueAccent,
            unfocusedBorderColor = NimbusCardBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RouteDepartureSelector(
    selectedMinutes: Int,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.route_planner_departure),
            style = MaterialTheme.typography.labelLarge,
            color = NimbusTextSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0, 60, 180, 360).forEach { minutes ->
                val selected = selectedMinutes == minutes
                FilterChip(
                    selected = selected,
                    onClick = { onSelected(minutes) },
                    label = {
                        Text(
                            text = if (minutes == 0) {
                                stringResource(R.string.route_planner_depart_now)
                            } else {
                                stringResource(R.string.route_planner_depart_hours, minutes / 60)
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NimbusBlueAccent.copy(alpha = 0.22f),
                        selectedLabelColor = NimbusTextPrimary,
                        labelColor = NimbusTextSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = NimbusCardBorder,
                        selectedBorderColor = NimbusBlueAccent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RoutePlanSummary(
    risk: DrivingRouteRiskLevel,
    distanceText: String,
    durationText: String,
    waypointCount: Int,
) {
    val riskLabel = stringResource(risk.labelRes())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusCardBg.copy(alpha = 0.86f))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.route_planner_summary),
                        style = MaterialTheme.typography.labelLarge,
                        color = NimbusTextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.route_planner_distance_duration, distanceText, durationText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NimbusTextPrimary,
                    )
                }
                RouteRiskBadge(risk, riskLabel)
            }
            Text(
                text = stringResource(R.string.route_planner_waypoint_count, waypointCount),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextTertiary,
            )
        }
    }
}

@Composable
private fun RouteWaypointRow(
    waypoint: DrivingRouteWaypoint,
    settings: NimbusSettings,
) {
    val riskLabel = stringResource(waypoint.risk.labelRes())
    val timeLabel = WeatherFormatter.formatClockTime(waypoint.arrivalTime, settings)
    val waypointDescription = stringResource(
        R.string.route_planner_waypoint_cd,
        waypoint.label,
        timeLabel,
        riskLabel,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(waypoint.risk.color().copy(alpha = 0.12f))
            .semantics {
                contentDescription = waypointDescription
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
            RouteRiskBadge(waypoint.risk, riskLabel)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_precip),
                value = WeatherFormatter.formatPrecipitation(waypoint.conditions.precipitationMm, settings),
                modifier = Modifier.weight(1f),
            )
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_wind),
                value = WeatherFormatter.formatWindSpeed(
                    waypoint.conditions.windGustKmh ?: waypoint.conditions.windSpeedKmh,
                    settings,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_visibility),
                value = WeatherFormatter.formatVisibility(waypoint.conditions.visibilityMeters, settings),
                modifier = Modifier.weight(1f),
            )
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_ice),
                value = stringResource(
                    if (waypoint.conditions.iceRisk) {
                        R.string.route_planner_ice_yes
                    } else {
                        R.string.route_planner_ice_no
                    },
                ),
                modifier = Modifier.weight(1f),
            )
        }
        RouteMetric(
            label = stringResource(R.string.route_planner_metric_alerts),
            value = stringResource(R.string.route_planner_alert_count, waypoint.weatherAlerts.size),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RouteRiskBadge(
    risk: DrivingRouteRiskLevel,
    label: String,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(risk.color().copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = risk.color(),
            maxLines = 1,
        )
    }
}

@Composable
private fun RouteMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun RoutePlannerError.messageRes(): Int = when (this) {
    RoutePlannerError.DESTINATION_REQUIRED -> R.string.route_planner_error_destination_required
    RoutePlannerError.ORIGIN_REQUIRED -> R.string.route_planner_error_origin_required
    RoutePlannerError.ORIGIN_NOT_FOUND -> R.string.route_planner_error_origin_not_found
    RoutePlannerError.DESTINATION_NOT_FOUND -> R.string.route_planner_error_destination_not_found
    RoutePlannerError.WEATHER_UNAVAILABLE -> R.string.route_planner_error_weather_unavailable
    RoutePlannerError.SHARED_ROUTE_UNREADABLE -> R.string.route_planner_error_shared_unreadable
}

private fun DrivingRouteRiskLevel.labelRes(): Int = when (this) {
    DrivingRouteRiskLevel.CLEAR -> R.string.route_planner_risk_clear
    DrivingRouteRiskLevel.LOW -> R.string.route_planner_risk_low
    DrivingRouteRiskLevel.MODERATE -> R.string.route_planner_risk_moderate
    DrivingRouteRiskLevel.HIGH -> R.string.route_planner_risk_high
}

private fun DrivingRouteRiskLevel.color(): Color = when (this) {
    DrivingRouteRiskLevel.CLEAR -> NimbusSuccess
    DrivingRouteRiskLevel.LOW -> NimbusBlueAccent
    DrivingRouteRiskLevel.MODERATE -> NimbusWarning
    DrivingRouteRiskLevel.HIGH -> Color(0xFFFF6B6B)
}

private fun formatRouteDuration(minutes: Long): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours <= 0) {
        "${remainingMinutes}m"
    } else {
        "${hours}h ${remainingMinutes}m"
    }
}

private fun formatRouteDistance(km: Double, settings: NimbusSettings): String {
    return when (settings.visibilityUnit) {
        VisibilityUnit.MILES -> "${(km * 0.621371).roundToInt()} mi"
        VisibilityUnit.KM -> "${km.roundToInt()} km"
    }
}
