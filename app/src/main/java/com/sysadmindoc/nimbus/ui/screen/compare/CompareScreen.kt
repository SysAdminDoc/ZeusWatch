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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
    viewModel: CompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = LocalUnitSettings.current

    PredictiveBackScaffold(onBack = onBack) {
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().background(NimbusBackgroundGradient),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(
                Modifier.fillMaxSize().background(NimbusBackgroundGradient),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
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
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                ) {
                    Text(
                        state.error ?: "Something went wrong",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NimbusTextPrimary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) { Text("Retry") }
                }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LocationSelector(
                            label = "Location 1",
                            selected = state.location1,
                            locations = state.savedLocations,
                            onSelect = { viewModel.selectLocation1(it) },
                            modifier = Modifier.weight(1f),
                        )
                        LocationSelector(
                            label = "Location 2",
                            selected = state.location2,
                            locations = state.savedLocations,
                            onSelect = { viewModel.selectLocation2(it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison table
                if (state.weather1 != null && state.weather2 != null) {
                    val w1 = state.weather1!!
                    val w2 = state.weather2!!

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

                Spacer(modifier = Modifier.height(32.dp))
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
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selected?.name ?: "Select...",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (selected != null) NimbusTextPrimary else NimbusBlueAccent,
            )
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
    raw1: Double = 0.0,
    raw2: Double = 0.0,
) {
    val shouldHighlight = highlightLower && raw1 != raw2
    val color1 = if (shouldHighlight && raw1 < raw2) NimbusBlueAccent else NimbusTextPrimary
    val color2 = if (shouldHighlight && raw2 < raw1) NimbusBlueAccent else NimbusTextPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(NimbusCardBg)
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 2.dp),
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
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextTertiary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = value2,
                style = MaterialTheme.typography.bodyMedium,
                color = color2,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
