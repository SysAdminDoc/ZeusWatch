package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusError
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusSurface
import com.sysadmindoc.nimbus.ui.theme.NimbusSurfaceVariant
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    onLocationSelected: (Long) -> Unit,
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val saved by viewModel.savedLocations.collectAsStateWithLifecycle()
    val search by viewModel.searchState.collectAsStateWithLifecycle()
    val locationTemps by viewModel.locationTemps.collectAsStateWithLifecycle()
    val locationConditions by viewModel.locationConditions.collectAsStateWithLifecycle()

    LocationsContent(
        saved = saved,
        search = search,
        locationTemps = locationTemps,
        locationConditions = locationConditions,
        onBack = onBack,
        onLocationSelected = onLocationSelected,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onClearSearch = { viewModel.clearSearch() },
        onAddLocation = { viewModel.addLocation(it) },
        onRemoveLocation = { viewModel.removeLocation(it) },
        onMoveLocation = { from, to -> viewModel.moveLocation(from, to) },
    )
}

@Composable
internal fun LocationsContent(
    saved: List<SavedLocationEntity>,
    search: SearchState,
    locationTemps: Map<Long, Double> = emptyMap(),
    locationConditions: Map<Long, Pair<com.sysadmindoc.nimbus.data.model.WeatherCode, Boolean>> = emptyMap(),
    onBack: () -> Unit,
    onLocationSelected: (Long) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onAddLocation: (GeocodingResult) -> Unit = {},
    onRemoveLocation: (Long) -> Unit = {},
    onMoveLocation: (Int, Int) -> Unit = { _, _ -> },
) {
    PredictiveBackScaffold(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NimbusNavyDark)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NimbusTextPrimary)
            }
            Text(
                "Locations",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = NimbusTextPrimary,
            )
        }

        // Search bar
        SearchBar(
            query = search.query,
            isSearching = search.isSearching,
            onQueryChanged = onSearchQueryChanged,
            onClear = onClearSearch,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        LocationsList(
            saved = saved,
            search = search,
            locationTemps = locationTemps,
            locationConditions = locationConditions,
            onLocationSelected = onLocationSelected,
            onAddLocation = onAddLocation,
            onRemoveLocation = onRemoveLocation,
            onMoveLocation = onMoveLocation,
        )
        }
    }
}

@Composable
private fun LocationsList(
    saved: List<SavedLocationEntity>,
    search: SearchState,
    locationTemps: Map<Long, Double> = emptyMap(),
    locationConditions: Map<Long, Pair<com.sysadmindoc.nimbus.data.model.WeatherCode, Boolean>> = emptyMap(),
    onLocationSelected: (Long) -> Unit,
    onAddLocation: (GeocodingResult) -> Unit,
    onRemoveLocation: (Long) -> Unit,
    onMoveLocation: (Int, Int) -> Unit = { _, _ -> },
) {
    // Track drag state for reordering
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeight = 62f // Approximate dp height of each location row

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Search results (shown when query active)
        if (search.query.length >= 2) {
            if (search.results.isNotEmpty()) {
                item {
                    Text(
                        "Search Results",
                        style = MaterialTheme.typography.labelMedium,
                        color = NimbusTextTertiary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(search.results, key = { it.id }) { result ->
                    SearchResultItem(
                        result = result,
                        onAdd = { onAddLocation(result) },
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            } else if (!search.isSearching) {
                item {
                    Text(
                        search.error ?: "No results found",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (search.error != null) NimbusError else NimbusTextTertiary,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        // Saved locations
        if (saved.isNotEmpty()) {
            item {
                Text(
                    "Saved Locations",
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusTextTertiary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(saved.size, key = { saved[it].id }) { index ->
                val loc = saved[index]
                val isDragged = draggedIndex == index
                val condition = locationConditions[loc.id]
                SavedLocationItem(
                    location = loc,
                    temperature = locationTemps[loc.id],
                    weatherCode = condition?.first,
                    isDay = condition?.second ?: true,
                    onClick = { onLocationSelected(loc.id) },
                    onRemove = {
                        if (!loc.isCurrentLocation) onRemoveLocation(loc.id)
                    },
                    showDragHandle = !loc.isCurrentLocation,
                    modifier = if (isDragged) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffsetY }
                    } else Modifier,
                    onDragStart = { draggedIndex = index; dragOffsetY = 0f },
                    onDrag = { delta ->
                        dragOffsetY += delta
                        val targetIndex = (index + (dragOffsetY / itemHeight).toInt())
                            .coerceIn(0, saved.lastIndex)
                        if (targetIndex != index && targetIndex != draggedIndex) {
                            onMoveLocation(draggedIndex, targetIndex)
                            draggedIndex = targetIndex
                            dragOffsetY = 0f
                        }
                    },
                    onDragEnd = { draggedIndex = -1; dragOffsetY = 0f },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun SearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NimbusSurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = NimbusTextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search city or zip code...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = NimbusTextPrimary),
                cursorBrush = SolidColor(NimbusBlueAccent),
            )
        }

        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = NimbusBlueAccent,
                strokeWidth = 2.dp,
            )
        } else if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Clear, "Clear", tint = NimbusTextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: GeocodingResult,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusCardBg)
            .clickable(onClick = onAdd)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = NimbusBlueAccent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextPrimary,
            )
            val sub = listOfNotNull(result.admin1, result.country).joinToString(", ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
            }
        }
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add",
            tint = NimbusBlueAccent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SavedLocationItem(
    location: SavedLocationEntity,
    temperature: Double? = null,
    weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode? = null,
    isDay: Boolean = true,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    showDragHandle: Boolean = false,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDragHandle) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                tint = NimbusTextTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, offset ->
                                change.consume()
                                onDrag(offset.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    },
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = null,
                tint = NimbusBlueAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (location.isCurrentLocation) "My Location" else location.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = if (location.isCurrentLocation) {
                location.name
            } else {
                listOfNotNull(
                    location.region.ifBlank { null },
                    location.country.ifBlank { null }
                ).joinToString(", ")
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
            }
        }
        if (weatherCode != null) {
            com.sysadmindoc.nimbus.ui.component.WeatherIcon(
                weatherCode = weatherCode,
                isDay = isDay,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (temperature != null) {
            Text(
                text = com.sysadmindoc.nimbus.util.WeatherFormatter.formatTemperature(temperature, s),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (!location.isCurrentLocation) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = NimbusError.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
