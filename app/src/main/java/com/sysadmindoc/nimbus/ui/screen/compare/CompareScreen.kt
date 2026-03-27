package com.sysadmindoc.nimbus.ui.screen.compare

import androidx.compose.foundation.background
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
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
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
                Modifier.fillMaxSize().background(NimbusNavyDark),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(
                Modifier.fillMaxSize().background(NimbusNavyDark),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    .background(NimbusNavyDark)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NimbusTextPrimary)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = NimbusBlueAccent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Compare Weather",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = NimbusTextPrimary,
                    )
                }

                // Location selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison table
                if (state.weather1 != null && state.weather2 != null) {
                    val w1 = state.weather1!!
                    val w2 = state.weather2!!

                    CompareRow("Temperature",
                        WeatherFormatter.formatTemperature(w1.current.temperature, s),
                        WeatherFormatter.formatTemperature(w2.current.temperature, s),
                    )
                    CompareRow("Feels Like",
                        WeatherFormatter.formatTemperature(w1.current.feelsLike, s),
                        WeatherFormatter.formatTemperature(w2.current.feelsLike, s),
                    )
                    CompareRow("Condition",
                        w1.current.weatherCode.description,
                        w2.current.weatherCode.description,
                    )
                    CompareRow("Humidity",
                        "${w1.current.humidity}%",
                        "${w2.current.humidity}%",
                    )
                    CompareRow("Wind",
                        WeatherFormatter.formatWindSpeed(w1.current.windSpeed, w1.current.windDirection, s),
                        WeatherFormatter.formatWindSpeed(w2.current.windSpeed, w2.current.windDirection, s),
                    )
                    CompareRow("UV Index",
                        WeatherFormatter.formatUvIndex(w1.current.uvIndex),
                        WeatherFormatter.formatUvIndex(w2.current.uvIndex),
                    )
                    CompareRow("Pressure",
                        WeatherFormatter.formatPressure(w1.current.pressure, s),
                        WeatherFormatter.formatPressure(w2.current.pressure, s),
                    )
                    CompareRow("High / Low",
                        "${WeatherFormatter.formatTemperature(w1.current.dailyHigh, s)} / ${WeatherFormatter.formatTemperature(w1.current.dailyLow, s)}",
                        "${WeatherFormatter.formatTemperature(w2.current.dailyHigh, s)} / ${WeatherFormatter.formatTemperature(w2.current.dailyLow, s)}",
                    )

                    val precip1 = w1.daily.firstOrNull()?.precipitationProbability ?: 0
                    val precip2 = w2.daily.firstOrNull()?.precipitationProbability ?: 0
                    CompareRow("Rain Chance", "$precip1%", "$precip2%")
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
                .clip(RoundedCornerShape(12.dp))
                .background(NimbusSurfaceVariant)
                .clickable { expanded = true }
                .padding(12.dp),
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

@Composable
private fun CompareRow(label: String, value1: String, value2: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value1,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextPrimary,
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
                color = NimbusTextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
        HorizontalDivider(color = NimbusCardBorder)
    }
}
