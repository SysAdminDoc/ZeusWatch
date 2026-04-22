package com.sysadmindoc.nimbus.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.component.InlineNoticeCard
import com.sysadmindoc.nimbus.ui.component.PremiumMessageCard
import com.sysadmindoc.nimbus.ui.component.ScreenHeader
import com.sysadmindoc.nimbus.ui.component.WeatherCard
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration activity shown when user adds a widget to their home screen.
 * Lets them pick which saved location the widget should display.
 * Selecting "Default" uses the last GPS location.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var locationDao: SavedLocationDao

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            NimbusTheme {
                WidgetConfigScreen(
                    locationDao = locationDao,
                    onLocationSelected = { locationId ->
                        confirmWidget(locationId)
                    },
                )
            }
        }
    }

    private fun confirmWidget(locationId: Long?) {
        lifecycleScope.launch {
            WidgetLocationPrefs.setLocationId(this@WidgetConfigActivity, appWidgetId, locationId)
            WidgetRefreshWorker.schedule(this@WidgetConfigActivity)
            WidgetRefreshWorker.enqueueImmediate(this@WidgetConfigActivity)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun WidgetConfigScreen(
    locationDao: SavedLocationDao,
    onLocationSelected: (Long?) -> Unit,
) {
    var locations by remember { mutableStateOf<List<SavedLocationEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        locations = try { locationDao.getAll() } catch (_: Exception) { emptyList() }
    }

    val selectableLocations = widgetSelectableLocations(locations)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = "Choose Widget Location",
                subtitle = "Pick the forecast this widget should keep in view. You can always add more widgets for other places later.",
                eyebrow = "Home Screen Widget",
            )
        }

        item {
            InlineNoticeCard(
                title = "Quick setup",
                message = "Tapping a location finishes setup immediately and keeps this widget focused on that place.",
                icon = Icons.Filled.LocationOn,
            )
        }

        item {
            WeatherCard(title = "Recommended") {
                LocationOption(
                    name = "Follow App Location",
                    subtitle = "Uses ZeusWatch's current or last loaded location so the widget stays in sync.",
                    isCurrentLocation = true,
                    badge = "Flexible",
                    onClick = { onLocationSelected(null) },
                )
            }
        }

        if (selectableLocations.isEmpty()) {
            item {
                PremiumMessageCard(
                    title = "No saved places yet",
                    message = "You can finish setup with the app location now, then save favorite places inside ZeusWatch to pin more widgets later.",
                    icon = Icons.Filled.LocationOn,
                    primaryActionLabel = "Use App Location",
                    onPrimaryAction = { onLocationSelected(null) },
                )
            }
        } else {
            item {
                WeatherCard(title = "Saved Locations") {
                    Text(
                        text = "Choose a specific place when you want a dedicated home-screen forecast.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        selectableLocations.forEach { loc ->
                            LocationOption(
                                name = loc.name,
                                subtitle = listOfNotNull(
                                    loc.region.ifBlank { null },
                                    loc.country.ifBlank { null },
                                ).joinToString(", "),
                                isCurrentLocation = false,
                                badge = "Pinned",
                                onClick = { onLocationSelected(loc.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationOption(
    name: String,
    subtitle: String,
    isCurrentLocation: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
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
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NimbusBlueAccent.copy(alpha = if (isCurrentLocation) 0.16f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isCurrentLocation) Icons.Filled.MyLocation else Icons.Filled.LocationOn,
                contentDescription = null,
                tint = if (isCurrentLocation) NimbusBlueAccent else NimbusTextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = NimbusTextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(NimbusBlueAccent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = NimbusBlueAccent,
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = NimbusBlueAccent,
            modifier = Modifier.size(18.dp),
        )
    }
}

internal fun widgetSelectableLocations(
    locations: List<SavedLocationEntity>,
): List<SavedLocationEntity> = locations.filterNot { it.isCurrentLocation }
