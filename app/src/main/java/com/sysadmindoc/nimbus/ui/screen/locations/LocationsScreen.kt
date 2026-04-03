package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusError
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
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
                .background(NimbusBackgroundGradient)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
        // Top bar
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
                    "Locations",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NimbusTextPrimary,
                )
                Text(
                    "${saved.size} saved places",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }

        Text(
            "Search, reorder, and jump between your favorite weather spots.",
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

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
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Search Locations",
            style = MaterialTheme.typography.labelLarge,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(22.dp)),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = NimbusTextPrimary),
            placeholder = {
                Text(
                    "Search city or zip code...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextTertiary,
                )
            },
            label = {
                Text(
                    "City, ZIP, or region",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = NimbusTextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                when {
                    isSearching -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NimbusBlueAccent,
                        strokeWidth = 2.dp,
                    )
                    query.isNotEmpty() -> IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Clear, "Clear", tint = NimbusTextTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = NimbusCardBg,
                unfocusedContainerColor = NimbusCardBg,
                disabledContainerColor = NimbusCardBg,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = NimbusBlueAccent,
                focusedTextColor = NimbusTextPrimary,
                unfocusedTextColor = NimbusTextPrimary,
                focusedLabelColor = NimbusBlueAccent,
                unfocusedLabelColor = NimbusTextSecondary,
                focusedLeadingIconColor = NimbusBlueAccent,
                unfocusedLeadingIconColor = NimbusTextTertiary,
                focusedTrailingIconColor = NimbusTextSecondary,
                unfocusedTrailingIconColor = NimbusTextSecondary,
                focusedPlaceholderColor = NimbusTextTertiary,
                unfocusedPlaceholderColor = NimbusTextTertiary,
            ),
        )
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.78f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NimbusBlueAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = NimbusBlueAccent,
                modifier = Modifier.size(18.dp),
            )
        }
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (location.isCurrentLocation) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = 0.16f),
                            NimbusGlassBottom,
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.78f),
                            NimbusSurfaceVariant,
                        )
                    },
                ),
            )
            .border(
                1.dp,
                if (location.isCurrentLocation) NimbusBlueAccent.copy(alpha = 0.55f) else NimbusCardBorder,
                RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
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
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NimbusBlueAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = NimbusBlueAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NimbusCardBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                com.sysadmindoc.nimbus.ui.component.WeatherIcon(
                    weatherCode = weatherCode,
                    isDay = isDay,
                    modifier = Modifier.size(20.dp),
                )
            }
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
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(NimbusError.copy(alpha = 0.12f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Remove", tint = NimbusError.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
