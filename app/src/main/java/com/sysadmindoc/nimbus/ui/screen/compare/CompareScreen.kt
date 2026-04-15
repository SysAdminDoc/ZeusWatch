package com.sysadmindoc.nimbus.ui.screen.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.WeatherIcon
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusSurfaceVariant
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter

/**
 * Side-by-side weather comparison between two locations.
 */
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    onNavigateToLocations: () -> Unit = {},
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = LocalUnitSettings.current

    PredictiveBackScaffold(onBack = onBack) {
        when {
            state.isLoading && state.savedLocations.isEmpty() -> Box(
                Modifier.fillMaxSize().background(NimbusBackgroundGradient),
                contentAlignment = Alignment.Center,
            ) {
                CompareStateCard(
                    title = "Preparing comparison",
                    message = "Loading your saved places and the latest forecast context.",
                    loading = true,
                )
            }
            shouldShowCompareFullScreenError(state) -> Box(
                Modifier.fillMaxSize().background(NimbusBackgroundGradient),
                contentAlignment = Alignment.Center,
            ) {
                CompareStateCard(
                    title = "Comparison unavailable",
                    message = state.error ?: "Something went wrong",
                    actionLabel = "Retry",
                    onAction = { viewModel.retry() },
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NimbusBackgroundGradient)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NimbusGlassTop.copy(alpha = 0.76f),
                                        NimbusGlassBottom,
                                    ),
                                ),
                            )
                            .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NimbusTextPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            "Compare Weather",
                            style = MaterialTheme.typography.headlineLarge,
                            color = NimbusTextPrimary,
                        )
                        Text(
                            "Live side-by-side conditions for any two saved locations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NimbusTextSecondary,
                        )
                    }
                }

                if (state.savedLocations.isNotEmpty()) {
                    CompareIntroCard(
                        readyCount = state.savedLocations.size,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (state.savedLocations.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NimbusGlassTop.copy(alpha = 0.78f),
                                        NimbusGlassBottom,
                                    ),
                                ),
                            )
                            .border(1.dp, NimbusCardBorder, RoundedCornerShape(28.dp))
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Choose two saved places to compare right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NimbusTextSecondary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                            LocationSelector(
                                label = "Primary",
                                selected = state.location1,
                                locations = state.savedLocations.filter {
                                    it.id == state.location1?.id || it.id != state.location2?.id
                                },
                                onSelect = { viewModel.selectLocation1(it) },
                                modifier = Modifier.weight(1f),
                            )
                            LocationSelector(
                                label = "Compare Against",
                                selected = state.location2,
                                locations = state.savedLocations.filter {
                                    it.id == state.location2?.id || it.id != state.location1?.id
                                },
                                onSelect = { viewModel.selectLocation2(it) },
                                modifier = Modifier.weight(1f),
                            )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    state.savedLocations.isEmpty() -> {
                        CompareEmptyState(
                            title = "Add a Location to Start Comparing",
                            message = "Save at least two places to see side-by-side weather, travel differences, and rain risk in one glance.",
                            actionLabel = "Open Locations",
                            onAction = onNavigateToLocations,
                        )
                    }
                    state.savedLocations.size == 1 -> {
                        CompareEmptyState(
                            title = "Add One More Location",
                            message = "${state.location1?.name ?: "Your current location"} is ready. Save one more place to unlock side-by-side comparisons.",
                            actionLabel = "Add Another Location",
                            onAction = onNavigateToLocations,
                        )
                    }
                    state.error != null -> {
                        CompareEmptyState(
                            title = "Couldn't Load Comparison",
                            message = state.error ?: "Something went wrong while comparing locations.",
                            actionLabel = "Retry",
                            onAction = { viewModel.retry() },
                        )
                    }
                    state.weather1 == null || state.weather2 == null -> {
                        CompareEmptyState(
                            title = "Loading Comparison",
                            message = "Pulling fresh forecast data for both locations now.",
                        )
                    }
                    else -> {
                    val w1 = state.weather1!!
                    val w2 = state.weather2!!

                    CompareSummaryCard(
                        location1 = state.location1,
                        location2 = state.location2,
                        weather1 = w1,
                        weather2 = w2,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    // Weather condition icons row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NimbusGlassTop.copy(alpha = 0.82f),
                                        NimbusGlassBottom,
                                    ),
                                ),
                            )
                            .border(1.dp, NimbusCardBorder, RoundedCornerShape(30.dp))
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WeatherIcon(
                                weatherCode = w1.current.weatherCode,
                                isDay = w1.current.isDay,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                WeatherFormatter.formatTemperature(w1.current.temperature, s),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = NimbusTextPrimary,
                            )
                            Text(
                                w1.current.weatherCode.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = NimbusTextSecondary,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = NimbusTextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WeatherIcon(
                                weatherCode = w2.current.weatherCode,
                                isDay = w2.current.isDay,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                WeatherFormatter.formatTemperature(w2.current.temperature, s),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = NimbusTextPrimary,
                            )
                            Text(
                                w2.current.weatherCode.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = NimbusTextSecondary,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    CompareRow("Feels Like",
                        WeatherFormatter.formatTemperature(w1.current.feelsLike, s),
                        WeatherFormatter.formatTemperature(w2.current.feelsLike, s),
                    )
                    CompareRow("Humidity",
                        "${w1.current.humidity}%",
                        "${w2.current.humidity}%",
                        highlightLower = true,
                        raw1 = w1.current.humidity.toDouble(),
                        raw2 = w2.current.humidity.toDouble(),
                    )
                    CompareRow("Wind",
                        WeatherFormatter.formatWindSpeed(w1.current.windSpeed, w1.current.windDirection, s),
                        WeatherFormatter.formatWindSpeed(w2.current.windSpeed, w2.current.windDirection, s),
                    )
                    CompareRow("UV Index",
                        WeatherFormatter.formatUvIndex(w1.current.uvIndex),
                        WeatherFormatter.formatUvIndex(w2.current.uvIndex),
                        highlightLower = true,
                        raw1 = w1.current.uvIndex,
                        raw2 = w2.current.uvIndex,
                    )
                    CompareRow("Pressure",
                        WeatherFormatter.formatPressure(w1.current.pressure, s),
                        WeatherFormatter.formatPressure(w2.current.pressure, s),
                    )
                    CompareRow("Visibility",
                        WeatherFormatter.formatVisibility(w1.current.visibility, s),
                        WeatherFormatter.formatVisibility(w2.current.visibility, s),
                    )
                    CompareRow("Cloud Cover",
                        "${w1.current.cloudCover}%",
                        "${w2.current.cloudCover}%",
                        highlightLower = true,
                        raw1 = w1.current.cloudCover.toDouble(),
                        raw2 = w2.current.cloudCover.toDouble(),
                    )
                    CompareRow("High / Low",
                        "${WeatherFormatter.formatTemperature(w1.current.dailyHigh, s)} / ${WeatherFormatter.formatTemperature(w1.current.dailyLow, s)}",
                        "${WeatherFormatter.formatTemperature(w2.current.dailyHigh, s)} / ${WeatherFormatter.formatTemperature(w2.current.dailyLow, s)}",
                    )

                    val precip1 = w1.daily.firstOrNull()?.precipitationProbability ?: 0
                    val precip2 = w2.daily.firstOrNull()?.precipitationProbability ?: 0
                    CompareRow("Rain Chance", "$precip1%", "$precip2%",
                        highlightLower = true,
                        raw1 = precip1.toDouble(),
                        raw2 = precip2.toDouble(),
                    )
                }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CompareEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CompareStateCard(
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    )
}

@Composable
private fun CompareStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .then(modifier)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.74f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(NimbusBlueAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = NimbusBlueAccent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = NimbusBlueAccent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = NimbusTextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
            textAlign = TextAlign.Center,
        )
        if (!loading && actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun LocationSelector(
    label: String,
    selected: SavedLocationEntity?,
    locations: List<SavedLocationEntity>,
    onSelect: (SavedLocationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 86.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop.copy(alpha = 0.72f),
                            NimbusSurfaceVariant,
                        ),
                    ),
                )
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (selected != null) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.45f)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = selected?.let { if (it.isCurrentLocation) "My Location" else it.name } ?: "Choose location",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (selected != null) NimbusTextPrimary else NimbusBlueAccent,
            )
            selected?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (it.isCurrentLocation) "Current device location" else listOfNotNull(it.region.takeIf(String::isNotBlank), it.country.takeIf(String::isNotBlank)).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    maxLines = 1,
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            locations.forEach { loc ->
                DropdownMenuItem(
                    text = { Text(if (loc.isCurrentLocation) "My Location" else loc.name) },
                    onClick = {
                        expanded = false
                        onSelect(loc)
                    },
                )
            }
        }
    }
}

/**
 * Comparison row with optional value highlighting.
 * When [highlightLower] is true, the lower numeric value gets an accent color
 * (useful for UV, humidity, rain chance where lower is better).
 * When [raw1]/[raw2] are provided, they're used for comparison instead of parsing strings.
 */
@Composable
private fun CompareRow(
    label: String,
    value1: String,
    value2: String,
    highlightLower: Boolean = false,
    raw1: Double? = null,
    raw2: Double? = null,
) {
    val (highlightFirst, highlightSecond) = highlightedCompareSides(highlightLower, raw1, raw2)
    val color1 = if (highlightFirst) NimbusBlueAccent else NimbusTextPrimary
    val color2 = if (highlightSecond) NimbusBlueAccent else NimbusTextPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.48f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value1,
                style = MaterialTheme.typography.bodyMedium,
                color = color1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (highlightFirst) NimbusBlueAccent.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusTextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = value2,
                style = MaterialTheme.typography.bodyMedium,
                color = color2,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (highlightSecond) NimbusBlueAccent.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompareIntroCard(
    readyCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.74f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(NimbusBlueAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = NimbusBlueAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Comparison deck ready",
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$readyCount saved places available for side-by-side weather decisions.",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }
    }
}

@Composable
private fun CompareSummaryCard(
    location1: SavedLocationEntity?,
    location2: SavedLocationEntity?,
    weather1: WeatherData,
    weather2: WeatherData,
    modifier: Modifier = Modifier,
) {
    val tempDelta = weather1.current.temperature - weather2.current.temperature
    val deltaMagnitude = kotlin.math.abs(tempDelta)
    val leadingName = if (tempDelta >= 0) {
        location1?.let { if (it.isCurrentLocation) "My Location" else it.name } ?: weather1.location.name
    } else {
        location2?.let { if (it.isCurrentLocation) "My Location" else it.name } ?: weather2.location.name
    }
    val trailingName = if (tempDelta >= 0) {
        location2?.let { if (it.isCurrentLocation) "My Location" else it.name } ?: weather2.location.name
    } else {
        location1?.let { if (it.isCurrentLocation) "My Location" else it.name } ?: weather1.location.name
    }
    val descriptor = when {
        deltaMagnitude < 0.75 -> "currently feels nearly identical"
        tempDelta >= 0 -> "is warmer right now"
        else -> "is cooler right now"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusBlueAccent.copy(alpha = 0.12f),
                        NimbusGlassTop.copy(alpha = 0.72f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.24f), RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Snapshot",
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
        )
        Text(
            text = if (deltaMagnitude < 0.75) {
                "$leadingName and $trailingName $descriptor."
            } else {
                "$leadingName $descriptor than $trailingName by about ${kotlin.math.round(deltaMagnitude).toInt()}°."
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompareSummaryPill(
                text = "Humidity ${weather1.current.humidity}% vs ${weather2.current.humidity}%",
                modifier = Modifier.weight(1f),
            )
            CompareSummaryPill(
                text = "Rain ${weather1.daily.firstOrNull()?.precipitationProbability ?: 0}% vs ${weather2.daily.firstOrNull()?.precipitationProbability ?: 0}%",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompareSummaryPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun highlightedCompareSides(
    highlightLower: Boolean,
    raw1: Double?,
    raw2: Double?,
): Pair<Boolean, Boolean> {
    if (!highlightLower || raw1 == null || raw2 == null || raw1 == raw2) return false to false
    return (raw1 < raw2) to (raw2 < raw1)
}

internal fun shouldShowCompareFullScreenError(state: CompareUiState): Boolean {
    return state.error != null && state.savedLocations.isEmpty()
}
