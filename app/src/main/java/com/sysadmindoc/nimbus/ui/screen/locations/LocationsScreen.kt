package com.sysadmindoc.nimbus.ui.screen.locations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.api.GeocodingResult
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.repository.WeatherDataType
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import com.sysadmindoc.nimbus.ui.component.InlineNoticeCard
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.ScreenHeader
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusError
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusSurfaceVariant
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.displayNameRes

@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    onLocationSelected: (Long) -> Unit,
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val saved by viewModel.savedLocations.collectAsStateWithLifecycle()
    val search by viewModel.searchState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val locationTemps by viewModel.locationTemps.collectAsStateWithLifecycle()
    val locationConditions by viewModel.locationConditions.collectAsStateWithLifecycle()

    LocationsContent(
        saved = saved,
        search = search,
        recentSearches = recentSearches,
        locationTemps = locationTemps,
        locationConditions = locationConditions,
        onBack = onBack,
        onLocationSelected = onLocationSelected,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onClearSearch = { viewModel.clearSearch() },
        onAddLocation = { result ->
            viewModel.addLocation(result) { locationId ->
                onLocationSelected(locationId)
            }
        },
        onRemoveLocation = { viewModel.removeLocation(it) },
        onMoveLocation = { from, to -> viewModel.moveLocation(from, to) },
        onCommitReorder = { viewModel.commitReorder(it) },
        onForecastSourceSelected = { locationId, provider ->
            viewModel.setForecastSource(locationId, provider)
        },
        onAlertSourceSelected = { locationId, provider ->
            viewModel.setAlertSource(locationId, provider)
        },
    )
}

@Composable
internal fun LocationsContent(
    saved: List<SavedLocationEntity>,
    search: SearchState,
    recentSearches: List<GeocodingResult> = emptyList(),
    locationTemps: Map<Long, Double> = emptyMap(),
    locationConditions: Map<Long, Pair<com.sysadmindoc.nimbus.data.model.WeatherCode, Boolean>> = emptyMap(),
    onBack: () -> Unit,
    onLocationSelected: (Long) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onAddLocation: (GeocodingResult) -> Unit = {},
    onRemoveLocation: (Long) -> Unit = {},
    onMoveLocation: (Int, Int) -> Unit = { _, _ -> },
    onCommitReorder: (List<Long>) -> Unit = {},
    onForecastSourceSelected: (Long, WeatherSourceProvider?) -> Unit = { _, _ -> },
    onAlertSourceSelected: (Long, WeatherSourceProvider?) -> Unit = { _, _ -> },
) {
    PredictiveBackScaffold(onBack = onBack) {
        val emptySubtitle = stringResource(R.string.locations_empty_subtitle)
        val savedCountSubtitle = stringResource(R.string.locations_saved_count_subtitle, saved.size)
        val savedPlacesSubtitle = if (saved.isEmpty()) {
            emptySubtitle
        } else {
            savedCountSubtitle
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NimbusBackgroundGradient)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ScreenHeader(
                title = stringResource(R.string.locations_title),
                subtitle = savedPlacesSubtitle,
                eyebrow = stringResource(R.string.locations_eyebrow),
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

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
                recentSearches = recentSearches,
                locationTemps = locationTemps,
                locationConditions = locationConditions,
                onLocationSelected = onLocationSelected,
                onAddLocation = onAddLocation,
                onRemoveLocation = onRemoveLocation,
                onMoveLocation = onMoveLocation,
                onCommitReorder = onCommitReorder,
                onForecastSourceSelected = onForecastSourceSelected,
                onAlertSourceSelected = onAlertSourceSelected,
            )
        }
    }
}

@Composable
private fun LocationsList(
    saved: List<SavedLocationEntity>,
    search: SearchState,
    recentSearches: List<GeocodingResult> = emptyList(),
    locationTemps: Map<Long, Double> = emptyMap(),
    locationConditions: Map<Long, Pair<com.sysadmindoc.nimbus.data.model.WeatherCode, Boolean>> = emptyMap(),
    onLocationSelected: (Long) -> Unit,
    onAddLocation: (GeocodingResult) -> Unit,
    onRemoveLocation: (Long) -> Unit,
    onMoveLocation: (Int, Int) -> Unit = { _, _ -> },
    onCommitReorder: (List<Long>) -> Unit = {},
    onForecastSourceSelected: (Long, WeatherSourceProvider?) -> Unit = { _, _ -> },
    onAlertSourceSelected: (Long, WeatherSourceProvider?) -> Unit = { _, _ -> },
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragList by remember(saved) { mutableStateOf(saved) }
    var itemHeightsPx by remember(saved) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    val displayList = if (draggedIndex >= 0) dragList else saved
    val fallbackItemHeightPx = with(LocalDensity.current) { 62.dp.toPx() }
    val visibleSearchResults = filterDuplicateSearchResults(search.results, saved)
    val visibleRecentSearches = filterDuplicateSearchResults(recentSearches, saved)
    val currentLocation = saved.firstOrNull { it.isCurrentLocation }
    val searchEmptyMessage = locationsSearchEmptyMessage(
        search = search,
        visibleResults = visibleSearchResults,
        alreadySavedMessage = stringResource(R.string.locations_already_saved_message),
        noResultsMessage = stringResource(R.string.locations_no_results_message),
        errorMessage = search.errorRes?.let { stringResource(it) },
    )
    val minimumMovableIndex = saved.indexOfFirst { !it.isCurrentLocation }.takeIf { it >= 0 } ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (search.query.length < 2) {
            locationDiscoveryItems(
                currentLocation = currentLocation,
                recentSearches = visibleRecentSearches,
                onLocationSelected = onLocationSelected,
                onAddLocation = onAddLocation,
            )
        }

        // Search results (shown when query active)
        if (search.query.length >= 2) {
            if (visibleSearchResults.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.locations_search_results),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NimbusTextTertiary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(visibleSearchResults, key = { it.id }) { result ->
                    SearchResultItem(
                        result = result,
                        onAdd = { onAddLocation(result) },
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            } else if (searchEmptyMessage != null) {
                item {
                    LocationsCalloutCard(
                        title = when {
                            search.errorRes != null -> stringResource(R.string.locations_search_unavailable)
                            search.results.isNotEmpty() -> stringResource(R.string.locations_already_saved_title)
                            else -> stringResource(R.string.locations_no_matches_title)
                        },
                        message = searchEmptyMessage,
                        icon = when {
                            search.errorRes != null -> Icons.Filled.Close
                            else -> Icons.Filled.Search
                        },
                        tint = if (search.errorRes != null) NimbusError else NimbusBlueAccent,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        // Saved locations
        if (saved.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.locations_saved_locations),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NimbusTextTertiary,
                    )
                    if (saved.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.locations_reorder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = NimbusTextSecondary,
                        )
                    }
                }
            }
            items(displayList.size, key = { displayList[it].id }) { index ->
                val loc = displayList[index]
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
                    onForecastSourceSelected = { provider ->
                        onForecastSourceSelected(loc.id, provider)
                    },
                    onAlertSourceSelected = { provider ->
                        onAlertSourceSelected(loc.id, provider)
                    },
                    showDragHandle = !loc.isCurrentLocation,
                    modifier = if (isDragged) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffsetY }
                    } else Modifier,
                    onDragStart = {
                        dragList = saved.toList()
                        draggedIndex = index
                        dragOffsetY = 0f
                    },
                    onDrag = { delta ->
                        dragOffsetY += delta
                        val currentIndex = draggedIndex.takeIf { it >= 0 } ?: index
                        val currentItemHeightPx = displayList.getOrNull(currentIndex)
                            ?.let { itemHeightsPx[it.id]?.toFloat() }
                            ?: fallbackItemHeightPx
                        val targetIndex = computeDraggedLocationTargetIndex(
                            currentIndex = currentIndex,
                            dragOffsetPx = dragOffsetY,
                            itemHeightPx = currentItemHeightPx,
                            minimumIndex = minimumMovableIndex,
                            lastIndex = displayList.lastIndex,
                        )
                        if (targetIndex != currentIndex) {
                            val mutable = dragList.toMutableList()
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            dragList = mutable
                            draggedIndex = targetIndex
                            dragOffsetY = 0f
                        }
                    },
                    onDragEnd = {
                        if (dragList != saved) {
                            onCommitReorder(dragList.map { it.id })
                        }
                        draggedIndex = -1
                        dragOffsetY = 0f
                    },
                    onMeasuredHeight = { heightPx ->
                        if (heightPx > 0 && itemHeightsPx[loc.id] != heightPx) {
                            itemHeightsPx = itemHeightsPx + (loc.id to heightPx)
                        }
                    },
                )
            }
        } else if (search.query.length < 2) {
            item {
                LocationsCalloutCard(
                    title = stringResource(R.string.locations_empty_title),
                    message = stringResource(R.string.locations_empty_message),
                    icon = Icons.Filled.LocationOn,
                    tint = NimbusBlueAccent,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

private fun LazyListScope.locationDiscoveryItems(
    currentLocation: SavedLocationEntity?,
    recentSearches: List<GeocodingResult>,
    onLocationSelected: (Long) -> Unit,
    onAddLocation: (GeocodingResult) -> Unit,
) {
    if (currentLocation != null) {
        item(key = "current_location_quick_action") {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.locations_quick_start),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NimbusTextTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                CurrentLocationQuickAction(
                    location = currentLocation,
                    onClick = { onLocationSelected(currentLocation.id) },
                )
            }
        }
    }

    if (recentSearches.isNotEmpty()) {
        item(key = "recent_searches_header") {
            Text(
                stringResource(R.string.locations_recent_searches),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NimbusTextTertiary,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        items(recentSearches, key = { "recent_${it.id}" }) { result ->
            SearchResultItem(
                result = result,
                onAdd = { onAddLocation(result) },
            )
        }
        item(key = "recent_searches_spacer") { Spacer(modifier = Modifier.height(12.dp)) }
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
            stringResource(R.string.locations_search_label),
            style = MaterialTheme.typography.labelLarge,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp)),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = NimbusTextPrimary),
            placeholder = {
                Text(
                    stringResource(R.string.locations_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextTertiary,
                )
            },
            label = {
                Text(
                    stringResource(R.string.locations_search_field_label),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.locations_search_icon_cd),
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
                    query.isNotEmpty() -> LocationIconAction(
                        icon = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.common_clear),
                        tint = NimbusTextTertiary,
                        onClick = onClear,
                    )
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
private fun CurrentLocationQuickAction(
    location: SavedLocationEntity,
    onClick: () -> Unit,
) {
    val contentDescription = stringResource(R.string.locations_use_current_location_cd, location.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusBlueAccent.copy(alpha = 0.16f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusBlueAccent.copy(alpha = 0.18f)),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.locations_use_current_location),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextPrimary,
            )
            Text(
                location.name,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: GeocodingResult,
    onAdd: () -> Unit,
) {
    val addResultDescription = remember(result.name, result.admin1, result.country) {
        listOfNotNull(result.admin1, result.country).joinToString(", ")
    }.let { region ->
        if (region.isNotBlank()) {
            stringResource(R.string.locations_add_result_with_region_cd, result.name, region)
        } else {
            stringResource(R.string.locations_add_result_cd, result.name)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.78f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
            .clickable(
                onClick = onAdd,
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = addResultDescription
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
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
            contentDescription = null,
            tint = NimbusBlueAccent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SavedLocationItem(
    location: SavedLocationEntity,
    modifier: Modifier = Modifier,
    temperature: Double? = null,
    weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode? = null,
    isDay: Boolean = true,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onForecastSourceSelected: (WeatherSourceProvider?) -> Unit = {},
    onAlertSourceSelected: (WeatherSourceProvider?) -> Unit = {},
    showDragHandle: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onMeasuredHeight: (Int) -> Unit = {},
) {
    var sourcePanelExpanded by remember(location.id) { mutableStateOf(false) }
    val displayName = if (location.isCurrentLocation) {
        stringResource(R.string.common_my_location)
    } else {
        location.name
    }
    val openWeatherDescription = stringResource(R.string.locations_open_weather_cd, displayName)
    val dragReorderDescription = stringResource(R.string.locations_drag_reorder_cd)
    val removeDescription = stringResource(R.string.locations_remove_cd, location.name)
    val sourceSettingsDescription = stringResource(R.string.locations_source_settings_cd, location.name)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onMeasuredHeight(it.size.height) },
    ) {
        SavedLocationRow(
            location = location,
            displayName = displayName,
            preview = SavedLocationWeatherPreview(
                temperature = temperature,
                weatherCode = weatherCode,
                isDay = isDay,
            ),
            sourcePanelExpanded = sourcePanelExpanded,
            labels = SavedLocationRowLabels(
                openWeatherDescription = openWeatherDescription,
                dragReorderDescription = dragReorderDescription,
                removeDescription = removeDescription,
                sourceSettingsDescription = sourceSettingsDescription,
            ),
            showDragHandle = showDragHandle,
            actions = SavedLocationRowActions(
                onClick = onClick,
                onRemove = onRemove,
                onSourceToggle = { sourcePanelExpanded = !sourcePanelExpanded },
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            ),
        )

        AnimatedVisibility(
            visible = sourcePanelExpanded && !location.isCurrentLocation,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
            ) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
            ) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            ),
        ) {
            LocationSourcePanel(
                location = location,
                onForecastSourceSelected = onForecastSourceSelected,
                onAlertSourceSelected = onAlertSourceSelected,
            )
        }
    }
}

private data class SavedLocationWeatherPreview(
    val temperature: Double?,
    val weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode?,
    val isDay: Boolean,
)

private data class SavedLocationRowLabels(
    val openWeatherDescription: String,
    val dragReorderDescription: String,
    val removeDescription: String,
    val sourceSettingsDescription: String,
)

private data class SavedLocationRowActions(
    val onClick: () -> Unit,
    val onRemove: () -> Unit,
    val onSourceToggle: () -> Unit,
    val onDragStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit,
)

@Composable
private fun SavedLocationRow(
    location: SavedLocationEntity,
    displayName: String,
    preview: SavedLocationWeatherPreview,
    sourcePanelExpanded: Boolean,
    showDragHandle: Boolean,
    labels: SavedLocationRowLabels,
    actions: SavedLocationRowActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(savedLocationBackground(location.isCurrentLocation))
            .border(
                1.dp,
                if (location.isCurrentLocation) NimbusBlueAccent.copy(alpha = 0.55f) else NimbusCardBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable(
                onClick = actions.onClick,
                role = Role.Button,
            )
            .semantics { contentDescription = labels.openWeatherDescription }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocationLeadingControl(
            showDragHandle = showDragHandle,
            dragReorderDescription = labels.dragReorderDescription,
            onDragStart = actions.onDragStart,
            onDrag = actions.onDrag,
            onDragEnd = actions.onDragEnd,
        )
        LocationNameBlock(location = location, displayName = displayName)
        LocationWeatherPreview(
            weatherCode = preview.weatherCode,
            isDay = preview.isDay,
            temperature = preview.temperature,
        )
        if (!location.isCurrentLocation) {
            LocationRowActions(
                sourcePanelExpanded = sourcePanelExpanded,
                removeDescription = labels.removeDescription,
                sourceSettingsDescription = labels.sourceSettingsDescription,
                onRemove = actions.onRemove,
                onSourceToggle = actions.onSourceToggle,
            )
        }
    }
}

private fun savedLocationBackground(isCurrentLocation: Boolean): Brush {
    return Brush.verticalGradient(
        colors = if (isCurrentLocation) {
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
    )
}

@Composable
private fun LocationLeadingControl(
    showDragHandle: Boolean,
    dragReorderDescription: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    if (showDragHandle) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusCardBg.copy(alpha = 0.72f))
                .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
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
                }
                .semantics {
                    contentDescription = dragReorderDescription
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = null,
                tint = NimbusTextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
    } else {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
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
}

@Composable
private fun RowScope.LocationNameBlock(
    location: SavedLocationEntity,
    displayName: String,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            displayName,
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
}

@Composable
private fun LocationWeatherPreview(
    weatherCode: com.sysadmindoc.nimbus.data.model.WeatherCode?,
    isDay: Boolean,
    temperature: Double?,
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current
    if (weatherCode != null) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
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
}

@Composable
private fun LocationRowActions(
    sourcePanelExpanded: Boolean,
    removeDescription: String,
    sourceSettingsDescription: String,
    onRemove: () -> Unit,
    onSourceToggle: () -> Unit,
) {
    LocationIconAction(
        icon = Icons.Filled.Tune,
        contentDescription = sourceSettingsDescription,
        tint = NimbusBlueAccent,
        highlighted = sourcePanelExpanded,
        onClick = onSourceToggle,
    )
    Spacer(modifier = Modifier.width(4.dp))
    LocationIconAction(
        icon = Icons.Filled.Close,
        contentDescription = removeDescription,
        tint = NimbusError,
        danger = true,
        onClick = onRemove,
    )
}

@Composable
private fun LocationSourcePanel(
    location: SavedLocationEntity,
    onForecastSourceSelected: (WeatherSourceProvider?) -> Unit,
    onAlertSourceSelected: (WeatherSourceProvider?) -> Unit,
) {
    val forecastSource = WeatherSourceProvider.fromStoredName(
        location.forecastSource,
        WeatherDataType.FORECAST,
    )
    val alertSource = WeatherSourceProvider.fromStoredName(
        location.alertSource,
        WeatherDataType.ALERTS,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusCardBg.copy(alpha = 0.82f))
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.locations_source_panel_title),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextSecondary,
        )
        LocationSourceDropdown(
            label = stringResource(R.string.locations_forecast_source),
            selected = forecastSource,
            options = WeatherSourceProvider.forType(WeatherDataType.FORECAST),
            onSelected = onForecastSourceSelected,
        )
        LocationSourceDropdown(
            label = stringResource(R.string.locations_alert_source),
            selected = alertSource,
            options = WeatherSourceProvider.forType(WeatherDataType.ALERTS),
            onSelected = onAlertSourceSelected,
        )
    }
}

@Composable
private fun LocationSourceDropdown(
    label: String,
    selected: WeatherSourceProvider?,
    options: List<WeatherSourceProvider>,
    onSelected: (WeatherSourceProvider?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selected?.let { stringResource(it.displayNameRes) }
        ?: stringResource(R.string.locations_use_default_source)
    val expandedLabel = stringResource(R.string.common_expanded)
    val collapsedLabel = stringResource(R.string.common_collapsed)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            SourceDropdownTrigger(
                label = label,
                selectedLabel = selectedLabel,
                expanded = expanded,
                expandedLabel = expandedLabel,
                collapsedLabel = collapsedLabel,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.locations_use_default_source)) },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    },
                )
                options.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(stringResource(provider.displayNameRes)) },
                        onClick = {
                            expanded = false
                            onSelected(provider)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> tint.copy(alpha = 0.62f)
            highlighted || pressed -> tint.copy(alpha = 0.42f)
            danger -> tint.copy(alpha = 0.26f)
            else -> NimbusCardBorder
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "locationIconBorder",
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            highlighted || focused || pressed -> tint.copy(alpha = if (pressed) 0.20f else 0.15f)
            danger -> tint.copy(alpha = 0.11f)
            else -> NimbusCardBg.copy(alpha = 0.68f)
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "locationIconBackground",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.97f else 1f
                scaleY = if (pressed) 0.97f else 1f
            }
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button,
            )
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint.copy(alpha = if (danger) 0.86f else 1f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SourceDropdownTrigger(
    label: String,
    selectedLabel: String,
    expanded: Boolean,
    expandedLabel: String,
    collapsedLabel: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (expanded || focused) {
            NimbusBlueAccent.copy(alpha = 0.58f)
        } else {
            NimbusCardBorder
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "sourceDropdownBorder",
    )

    Row(
        modifier = Modifier
            .widthIn(min = 132.dp, max = 178.dp)
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.98f else 1f
                scaleY = if (pressed) 0.98f else 1f
            }
            .clip(shape)
            .background(
                if (expanded || focused || pressed) {
                    NimbusBlueAccent.copy(alpha = 0.12f)
                } else {
                    NimbusGlassTop.copy(alpha = 0.58f)
                },
            )
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $selectedLabel"
                stateDescription = if (expanded) expandedLabel else collapsedLabel
            }
            .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            selectedLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = NimbusTextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LocationsCalloutCard(
    title: String,
    message: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    InlineNoticeCard(
        title = title,
        message = message,
        icon = icon,
        tint = tint,
        modifier = modifier,
    )
}

internal fun filterDuplicateSearchResults(
    results: List<GeocodingResult>,
    savedLocations: List<SavedLocationEntity>,
): List<GeocodingResult> {
    return results.filterNot { result ->
        savedLocations.any { location ->
            com.sysadmindoc.nimbus.data.model.matchesSavedLocation(result, location)
        }
    }
}

internal fun locationsSearchEmptyMessage(
    search: SearchState,
    visibleResults: List<GeocodingResult>,
    alreadySavedMessage: String = "Location already saved",
    noResultsMessage: String = "No results found",
    errorMessage: String? = null,
): String? {
    if (search.query.length < 2 || search.isSearching || visibleResults.isNotEmpty()) return null
    return when {
        search.errorRes != null -> errorMessage ?: noResultsMessage
        search.results.isNotEmpty() -> alreadySavedMessage
        else -> noResultsMessage
    }
}

internal fun computeDraggedLocationTargetIndex(
    currentIndex: Int,
    dragOffsetPx: Float,
    itemHeightPx: Float,
    minimumIndex: Int = 0,
    lastIndex: Int,
): Int {
    if (lastIndex < 0) return currentIndex
    if (itemHeightPx <= 0f) return currentIndex.coerceIn(minimumIndex, lastIndex)
    return (currentIndex + (dragOffsetPx / itemHeightPx).toInt())
        .coerceIn(minimumIndex, lastIndex)
}
