package com.sysadmindoc.nimbus.ui.screen.radar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.DrivingRouteEstimateKind
import com.sysadmindoc.nimbus.data.repository.DrivingRouteGeometry
import com.sysadmindoc.nimbus.data.repository.DrivingRouteRiskLevel
import com.sysadmindoc.nimbus.data.repository.DrivingRouteWaypoint
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusError
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyMid
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Immutable callback holder for [RouteWeatherPlannerSheet]. */
data class RoutePlannerActions(
    val onOriginChange: (String) -> Unit,
    val onDestinationChange: (String) -> Unit,
    val onDepartureOffsetChange: (Int) -> Unit,
    val onGpxImported: (DrivingRouteGeometry) -> Unit,
    val onGpxImportFailed: (GpxRouteParseFailure) -> Unit,
    val onClearGpx: () -> Unit,
    val onPlanRoute: () -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteWeatherPlannerSheet(
    state: RoutePlannerUiState,
    settings: NimbusSettings,
    actions: RoutePlannerActions,
) {
    if (!state.isSheetOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gpxParser = remember { GpxRouteParser() }
    val bottomSheetHandleDescription = stringResource(R.string.common_bottom_sheet_handle)
    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use(gpxParser::parse)
                            ?: throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX)
                    }
                }
                result.fold(
                    onSuccess = actions.onGpxImported,
                    onFailure = { error ->
                        actions.onGpxImportFailed(
                            (error as? GpxRouteParseException)?.reason ?: GpxRouteParseFailure.INVALID_GPX
                        )
                    },
                )
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        containerColor = NimbusNavyDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clearAndSetSemantics {
                        contentDescription = bottomSheetHandleDescription
                    },
            )
        },
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

            if (state.routeGeometry == null) {
                RoutePlannerTextField(
                    value = state.originQuery,
                    onValueChange = actions.onOriginChange,
                    label = stringResource(R.string.route_planner_origin_label),
                    placeholder = stringResource(R.string.route_planner_origin_placeholder),
                )
                RoutePlannerTextField(
                    value = state.destinationQuery,
                    onValueChange = actions.onDestinationChange,
                    label = stringResource(R.string.route_planner_destination_label),
                    placeholder = stringResource(R.string.route_planner_destination_placeholder),
                )
            } else {
                ImportedGpxSummary(
                    geometry = state.routeGeometry,
                    onClear = actions.onClearGpx,
                )
            }

            OutlinedButton(
                onClick = {
                    gpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml"))
                },
                enabled = !state.isPlanning,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.route_planner_import_gpx))
            }

            RouteDepartureSelector(
                selectedMinutes = state.departureOffsetMinutes,
                onSelected = actions.onDepartureOffsetChange,
            )

            state.error?.let { error ->
                val errorMessage = stringResource(error.messageRes())
                Text(
                    text = errorMessage,
                    color = NimbusWarning,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = errorMessage
                    },
                )
            }

            RoutePlannerSubmitButton(
                isPlanning = state.isPlanning,
                onPlanRoute = actions.onPlanRoute,
            )

            state.plan?.let { plan ->
                RoutePlanSummary(
                    risk = plan.risk,
                    distanceText = formatRouteDistance(plan.distanceKm, settings),
                    durationText = formatRouteDuration(plan.estimatedDurationMinutes),
                    waypointCount = plan.waypoints.size,
                    unavailableWaypointCount = plan.unavailableWaypointCount,
                    estimateKind = plan.estimateKind,
                    assumedSpeedText = WeatherFormatter.formatWindSpeed(plan.assumedSpeedKmh, settings),
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
private fun RoutePlannerSubmitButton(
    isPlanning: Boolean,
    onPlanRoute: () -> Unit,
) {
    Button(
        onClick = onPlanRoute,
        enabled = !isPlanning,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = NimbusNavyMid,
            disabledContentColor = NimbusTextTertiary,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        if (isPlanning) {
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
            text = if (isPlanning) {
                stringResource(R.string.route_planner_planning)
            } else {
                stringResource(R.string.route_planner_plan)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImportedGpxSummary(
    geometry: DrivingRouteGeometry,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusCardBg.copy(alpha = 0.86f))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (geometry.estimateKind == DrivingRouteEstimateKind.GPX_ROUTE) {
                    R.string.route_planner_gpx_route_loaded
                } else {
                    R.string.route_planner_gpx_track_loaded
                },
                geometry.points.size,
            ),
            color = NimbusTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.route_planner_gpx_clear))
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
    unavailableWaypointCount: Int,
    estimateKind: DrivingRouteEstimateKind,
    assumedSpeedText: String,
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
            Text(
                text = if (estimateKind == DrivingRouteEstimateKind.STRAIGHT_LINE_CORRIDOR) {
                    stringResource(
                        R.string.route_planner_corridor_disclaimer,
                        assumedSpeedText,
                    )
                } else {
                    stringResource(
                        R.string.route_planner_gpx_disclaimer,
                        assumedSpeedText,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
            if (unavailableWaypointCount > 0) {
                Text(
                    text = stringResource(
                        R.string.route_planner_partial_samples,
                        unavailableWaypointCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusWarning,
                )
            }
        }
    }
}

@Composable
private fun RouteWaypointRow(
    waypoint: DrivingRouteWaypoint,
    settings: NimbusSettings,
) {
    val conditions = waypoint.conditions
    val riskLabel = if (conditions == null) {
        stringResource(R.string.route_planner_unavailable)
    } else {
        stringResource(waypoint.risk.labelRes())
    }
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
            if (conditions != null) {
                RouteRiskBadge(waypoint.risk, riskLabel)
            }
        }

        if (conditions == null) {
            Text(
                text = stringResource(R.string.route_planner_waypoint_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusWarning,
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_precip),
                value = WeatherFormatter.formatPrecipitation(conditions.precipitationMm, settings),
                modifier = Modifier.weight(1f),
            )
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_wind),
                value = WeatherFormatter.formatWindSpeed(
                    conditions.windGustKmh ?: conditions.windSpeedKmh,
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
                value = WeatherFormatter.formatVisibility(conditions.visibilityMeters, settings),
                modifier = Modifier.weight(1f),
            )
            RouteMetric(
                label = stringResource(R.string.route_planner_metric_ice),
                value = stringResource(
                    if (conditions.iceRisk) {
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
            .clip(RoundedCornerShape(6.dp))
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
    RoutePlannerError.GPX_FILE_TOO_LARGE -> R.string.route_planner_error_gpx_file_too_large
    RoutePlannerError.GPX_TOO_MANY_POINTS -> R.string.route_planner_error_gpx_too_many_points
    RoutePlannerError.GPX_UNSAFE_XML -> R.string.route_planner_error_gpx_unsafe_xml
    RoutePlannerError.GPX_INVALID -> R.string.route_planner_error_gpx_invalid
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
    DrivingRouteRiskLevel.HIGH -> NimbusError
}

@Composable
private fun formatRouteDuration(minutes: Long): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours <= 0) {
        stringResource(R.string.route_planner_duration_minutes, remainingMinutes)
    } else {
        stringResource(R.string.route_planner_duration_short, hours, remainingMinutes)
    }
}

@Composable
private fun formatRouteDistance(km: Double, settings: NimbusSettings): String {
    return when (settings.visibilityUnit) {
        VisibilityUnit.MILES -> stringResource(R.string.route_planner_distance_mi, (km * 0.621371).roundToInt())
        VisibilityUnit.KM -> stringResource(R.string.route_planner_distance_km, km.roundToInt())
    }
}
