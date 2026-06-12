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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.component.LocalUnitSettings
import com.sysadmindoc.nimbus.ui.component.NimbusStatusBadge
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.PremiumMessageCard
import com.sysadmindoc.nimbus.ui.component.ScreenHeader
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
    val settings = LocalUnitSettings.current
    val actions = CompareScreenActions(
        onBack = onBack,
        onNavigateToLocations = onNavigateToLocations,
        onRetry = viewModel::retry,
        onSelectLocation1 = viewModel::selectLocation1,
        onSelectLocation2 = viewModel::selectLocation2,
    )

    PredictiveBackScaffold(onBack = onBack) {
        CompareScreenBody(state = state, settings = settings, actions = actions)
    }
}

private data class CompareScreenActions(
    val onBack: () -> Unit,
    val onNavigateToLocations: () -> Unit,
    val onRetry: () -> Unit,
    val onSelectLocation1: (SavedLocationEntity) -> Unit,
    val onSelectLocation2: (SavedLocationEntity) -> Unit,
)

@Composable
private fun CompareScreenBody(
    state: CompareUiState,
    settings: NimbusSettings,
    actions: CompareScreenActions,
) {
    when {
        state.isLoading && state.savedLocations.isEmpty() -> CompareFullScreenLoading()
        shouldShowCompareFullScreenError(state) -> CompareFullScreenError(state.error, actions.onRetry)
        else -> CompareScrollableContent(state, settings, actions)
    }
}

@Composable
private fun CompareFullScreenLoading() {
    CompareCenteredState {
        CompareStateCard(
            title = stringResource(R.string.compare_preparing_title),
            message = stringResource(R.string.compare_preparing_message),
            loading = true,
        )
    }
}

@Composable
private fun CompareFullScreenError(
    error: String?,
    onRetry: () -> Unit,
) {
    CompareCenteredState {
        CompareStateCard(
            title = stringResource(R.string.compare_unavailable_title),
            message = error ?: stringResource(R.string.common_something_went_wrong),
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun CompareCenteredState(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CompareScrollableContent(
    state: CompareUiState,
    settings: NimbusSettings,
    actions: CompareScreenActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.compare_title),
            subtitle = stringResource(R.string.compare_subtitle),
            eyebrow = stringResource(R.string.compare_eyebrow),
            onBack = actions.onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        CompareLocationControls(state, actions)
        Spacer(modifier = Modifier.height(16.dp))
        CompareResultSection(state, settings, actions)
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CompareLocationControls(
    state: CompareUiState,
    actions: CompareScreenActions,
) {
    if (state.savedLocations.isEmpty()) return

    CompareIntroCard(
        readyCount = state.savedLocations.size,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(modifier = Modifier.height(12.dp))
    CompareSelectorCard(state, actions)
}

@Composable
private fun CompareSelectorCard(
    state: CompareUiState,
    actions: CompareScreenActions,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(NimbusGlassTop.copy(alpha = 0.78f), NimbusGlassBottom),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.compare_choose_two),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
            CompareSelectorRow(state, actions)
        }
    }
}

@Composable
private fun CompareSelectorRow(
    state: CompareUiState,
    actions: CompareScreenActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LocationSelector(
            label = stringResource(R.string.compare_primary),
            selected = state.location1,
            locations = state.savedLocations.filter { it.id == state.location1?.id || it.id != state.location2?.id },
            onSelect = actions.onSelectLocation1,
            modifier = Modifier.weight(1f),
        )
        LocationSelector(
            label = stringResource(R.string.compare_against),
            selected = state.location2,
            locations = state.savedLocations.filter { it.id == state.location2?.id || it.id != state.location1?.id },
            onSelect = actions.onSelectLocation2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompareResultSection(
    state: CompareUiState,
    settings: NimbusSettings,
    actions: CompareScreenActions,
) {
    when {
        state.savedLocations.isEmpty() -> CompareNeedLocations(actions.onNavigateToLocations)
        state.savedLocations.size == 1 -> CompareNeedSecondLocation(state, actions.onNavigateToLocations)
        // One slot failed but the other loaded: keep the healthy slot's data
        // visible and scope the error card to the failed slot only.
        state.error != null && (state.weather1 != null || state.weather2 != null) ->
            ComparePartialWeather(state, settings, actions)
        state.error != null -> CompareLoadFailed(state.error, actions.onRetry)
        state.weather1 == null || state.weather2 == null -> CompareLoadingPair()
        else -> CompareLoadedWeather(state, settings)
    }
}

@Composable
private fun CompareNeedLocations(onNavigateToLocations: () -> Unit) {
    CompareEmptyState(
        title = stringResource(R.string.compare_add_location_title),
        message = stringResource(R.string.compare_add_location_message),
        actionLabel = stringResource(R.string.compare_open_locations),
        onAction = onNavigateToLocations,
    )
}

@Composable
private fun CompareNeedSecondLocation(
    state: CompareUiState,
    onNavigateToLocations: () -> Unit,
) {
    CompareEmptyState(
        title = stringResource(R.string.compare_add_one_more_title),
        message = stringResource(
            R.string.compare_add_one_more_message,
            state.location1?.name ?: stringResource(R.string.common_current_device_location),
        ),
        actionLabel = stringResource(R.string.compare_add_another_location),
        onAction = onNavigateToLocations,
    )
}

@Composable
private fun CompareLoadFailed(
    error: String?,
    onRetry: () -> Unit,
) {
    CompareEmptyState(
        title = stringResource(R.string.compare_load_failed_title),
        message = error ?: stringResource(R.string.compare_load_failed_message),
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
    )
}

@Composable
private fun CompareLoadingPair() {
    CompareEmptyState(
        title = stringResource(R.string.compare_loading_title),
        message = stringResource(R.string.compare_loading_message),
    )
}

/**
 * Partial result: one slot loaded, the other errored. Shows the healthy slot's
 * current conditions plus a retry card scoped to the failed slot.
 */
@Composable
private fun ComparePartialWeather(
    state: CompareUiState,
    settings: NimbusSettings,
    actions: CompareScreenActions,
) {
    val loadedWeather = state.weather1 ?: state.weather2 ?: return
    val loadedLocation = if (state.weather1 != null) state.location1 else state.location2
    val myLocationLabel = stringResource(R.string.common_my_location)
    val loadedName = loadedLocation?.let { if (it.isCurrentLocation) myLocationLabel else it.name }
        ?: loadedWeather.location.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(NimbusGlassTop.copy(alpha = 0.82f), NimbusGlassBottom),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = loadedName,
            style = MaterialTheme.typography.labelLarge,
            color = NimbusTextSecondary,
        )
        CompareConditionColumn(loadedWeather, settings)
    }
    Spacer(Modifier.height(12.dp))
    CompareLoadFailed(state.error, actions.onRetry)
}

@Composable
private fun CompareLoadedWeather(
    state: CompareUiState,
    settings: NimbusSettings,
) {
    val weather1 = state.weather1 ?: return
    val weather2 = state.weather2 ?: return

    CompareSummaryCard(
        location1 = state.location1,
        location2 = state.location2,
        weather1 = weather1,
        weather2 = weather2,
        settings = settings,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(12.dp))
    CompareConditionCard(weather1, weather2, settings)
    Spacer(Modifier.height(8.dp))
    CompareMetricRows(weather1, weather2, settings)
}

@Composable
private fun CompareConditionCard(
    weather1: WeatherData,
    weather2: WeatherData,
    settings: NimbusSettings,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(NimbusGlassTop.copy(alpha = 0.82f), NimbusGlassBottom),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompareConditionColumn(weather1, settings)
        Icon(
            Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = null,
            tint = NimbusTextTertiary,
            modifier = Modifier.size(20.dp),
        )
        CompareConditionColumn(weather2, settings)
    }
}

@Composable
private fun CompareConditionColumn(
    weather: WeatherData,
    settings: NimbusSettings,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WeatherIcon(
            weatherCode = weather.current.weatherCode,
            isDay = weather.current.isDay,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            WeatherFormatter.formatTemperature(weather.current.temperature, settings),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextPrimary,
        )
        Text(
            stringResource(weather.current.weatherCode.descriptionRes()),
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary,
        )
    }
}

@Composable
private fun CompareMetricRows(
    weather1: WeatherData,
    weather2: WeatherData,
    settings: NimbusSettings,
) {
    CompareRow(
        stringResource(R.string.compare_feels_like_label),
        WeatherFormatter.formatTemperature(weather1.current.feelsLike, settings),
        WeatherFormatter.formatTemperature(weather2.current.feelsLike, settings),
    )
    CompareWeatherQualityRows(weather1, weather2, settings)
    CompareDailyRows(weather1, weather2, settings)
}

@Composable
private fun CompareWeatherQualityRows(
    weather1: WeatherData,
    weather2: WeatherData,
    settings: NimbusSettings,
) {
    CompareRow(
        stringResource(R.string.humidity),
        "${weather1.current.humidity}%",
        "${weather2.current.humidity}%",
        highlightLower = true,
        raw1 = weather1.current.humidity.toDouble(),
        raw2 = weather2.current.humidity.toDouble(),
    )
    CompareRow(
        stringResource(R.string.wind),
        WeatherFormatter.formatWindSpeed(weather1.current.windSpeed, weather1.current.windDirection, settings),
        WeatherFormatter.formatWindSpeed(weather2.current.windSpeed, weather2.current.windDirection, settings),
    )
    CompareRow(
        stringResource(R.string.compare_uv_index_label),
        WeatherFormatter.formatUvIndex(weather1.current.uvIndex),
        WeatherFormatter.formatUvIndex(weather2.current.uvIndex),
        highlightLower = true,
        raw1 = weather1.current.uvIndex,
        raw2 = weather2.current.uvIndex,
    )
    CompareRow(
        stringResource(R.string.pressure),
        WeatherFormatter.formatPressure(weather1.current.pressure, settings),
        WeatherFormatter.formatPressure(weather2.current.pressure, settings),
    )
    CompareRow(
        stringResource(R.string.visibility),
        WeatherFormatter.formatVisibility(weather1.current.visibility, settings),
        WeatherFormatter.formatVisibility(weather2.current.visibility, settings),
    )
    CompareRow(
        stringResource(R.string.cloud_cover),
        "${weather1.current.cloudCover}%",
        "${weather2.current.cloudCover}%",
        highlightLower = true,
        raw1 = weather1.current.cloudCover.toDouble(),
        raw2 = weather2.current.cloudCover.toDouble(),
    )
}

@Composable
private fun CompareDailyRows(
    weather1: WeatherData,
    weather2: WeatherData,
    settings: NimbusSettings,
) {
    CompareRow(
        stringResource(R.string.compare_high_low_label),
        "${WeatherFormatter.formatTemperature(weather1.current.dailyHigh, settings)} / " +
            WeatherFormatter.formatTemperature(weather1.current.dailyLow, settings),
        "${WeatherFormatter.formatTemperature(weather2.current.dailyHigh, settings)} / " +
            WeatherFormatter.formatTemperature(weather2.current.dailyLow, settings),
    )

    val precip1 = weather1.daily.firstOrNull()?.precipitationProbability ?: 0
    val precip2 = weather2.daily.firstOrNull()?.precipitationProbability ?: 0
    CompareRow(
        stringResource(R.string.compare_rain_chance_label),
        "$precip1%",
        "$precip2%",
        highlightLower = true,
        raw1 = precip1.toDouble(),
        raw2 = precip2.toDouble(),
    )
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
    PremiumMessageCard(
        title = title,
        message = message,
        icon = Icons.AutoMirrored.Filled.CompareArrows,
        loading = loading,
        primaryActionLabel = actionLabel,
        onPrimaryAction = onAction,
        modifier = Modifier
            .then(modifier)
    )
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
    val myLocationLabel = stringResource(R.string.common_my_location)
    val chooseLocationLabel = stringResource(R.string.common_choose_location)
    val selectedLabel = selected?.let { if (it.isCurrentLocation) myLocationLabel else it.name }
        ?: chooseLocationLabel
    val expandedLabel = stringResource(R.string.common_expanded)
    val collapsedLabel = stringResource(R.string.common_collapsed)
    val selectorDescription = stringResource(R.string.compare_selector_cd, label, selectedLabel)

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 86.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NimbusGlassTop.copy(alpha = 0.72f),
                            NimbusSurfaceVariant,
                        ),
                    ),
                )
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
                .clickable(
                    onClick = { expanded = true },
                    role = Role.Button,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = selectorDescription
                    stateDescription = if (expanded) expandedLabel else collapsedLabel
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected != null) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.45f)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (selected != null) NimbusTextPrimary else NimbusBlueAccent,
            )
            selected?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (it.isCurrentLocation) {
                        stringResource(R.string.common_current_device_location)
                    } else {
                        listOfNotNull(it.region.takeIf(String::isNotBlank), it.country.takeIf(String::isNotBlank))
                            .joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    maxLines = 1,
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            locations.forEach { loc ->
                DropdownMenuItem(
                    text = { Text(if (loc.isCurrentLocation) myLocationLabel else loc.name) },
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
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.48f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
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
                    .clip(RoundedCornerShape(8.dp))
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
                    .clip(RoundedCornerShape(8.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.74f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
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
                text = stringResource(R.string.compare_ready_title),
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.compare_ready_message, readyCount),
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
    settings: NimbusSettings,
    modifier: Modifier = Modifier,
) {
    val myLocationLabel = stringResource(R.string.common_my_location)
    // Compare in display units: a Celsius delta shown to Fahrenheit users both
    // understated the difference and tripped the "nearly identical" threshold.
    val tempDelta = WeatherFormatter.convertedTemp(weather1.current.temperature, settings) -
        WeatherFormatter.convertedTemp(weather2.current.temperature, settings)
    val deltaMagnitude = kotlin.math.abs(tempDelta)
    val leadingName = if (tempDelta >= 0) {
        location1?.let { if (it.isCurrentLocation) myLocationLabel else it.name } ?: weather1.location.name
    } else {
        location2?.let { if (it.isCurrentLocation) myLocationLabel else it.name } ?: weather2.location.name
    }
    val trailingName = if (tempDelta >= 0) {
        location2?.let { if (it.isCurrentLocation) myLocationLabel else it.name } ?: weather2.location.name
    } else {
        location1?.let { if (it.isCurrentLocation) myLocationLabel else it.name } ?: weather1.location.name
    }
    val descriptor = if (tempDelta >= 0) {
        stringResource(R.string.compare_warmer)
    } else {
        stringResource(R.string.compare_cooler)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusBlueAccent.copy(alpha = 0.12f),
                        NimbusGlassTop.copy(alpha = 0.72f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.compare_snapshot),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextTertiary,
        )
        Text(
            text = if (deltaMagnitude < 0.75) {
                stringResource(R.string.compare_currently_identical, leadingName, trailingName)
            } else {
                stringResource(
                    R.string.compare_temperature_delta,
                    leadingName,
                    descriptor,
                    trailingName,
                    kotlin.math.round(deltaMagnitude).toInt(),
                )
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompareSummaryBadge(
                text = stringResource(
                    R.string.compare_humidity_summary,
                    weather1.current.humidity,
                    weather2.current.humidity,
                ),
                modifier = Modifier.weight(1f),
            )
            CompareSummaryBadge(
                text = stringResource(
                    R.string.compare_rain_summary,
                    weather1.daily.firstOrNull()?.precipitationProbability ?: 0,
                    weather2.daily.firstOrNull()?.precipitationProbability ?: 0,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompareSummaryBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    NimbusStatusBadge(
        text = text,
        tint = NimbusTextSecondary,
        modifier = modifier,
        maxLines = 2,
    )
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
