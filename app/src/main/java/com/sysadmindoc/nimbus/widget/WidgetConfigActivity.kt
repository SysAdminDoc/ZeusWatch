package com.sysadmindoc.nimbus.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.api.SavedLocationDao
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
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
                    appWidgetId = appWidgetId,
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

            // Trigger widget update
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun WidgetConfigScreen(
    appWidgetId: Int,
    locationDao: SavedLocationDao,
    onLocationSelected: (Long?) -> Unit,
) {
    var locations by remember { mutableStateOf<List<SavedLocationEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        locations = try { locationDao.getAll() } catch (_: Exception) { emptyList() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusNavyDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Choose Widget Location",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = NimbusTextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Select which location this widget should display",
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            // Default option (GPS / last location)
            item {
                LocationOption(
                    name = "Default (Current Location)",
                    subtitle = "Uses your GPS or last known location",
                    isCurrentLocation = true,
                    onClick = { onLocationSelected(null) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Saved locations
            items(locations) { loc ->
                LocationOption(
                    name = if (loc.isCurrentLocation) "My Location" else loc.name,
                    subtitle = if (loc.isCurrentLocation) "GPS location"
                    else listOfNotNull(
                        loc.region.ifBlank { null },
                        loc.country.ifBlank { null },
                    ).joinToString(", "),
                    isCurrentLocation = loc.isCurrentLocation,
                    onClick = { onLocationSelected(loc.id) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LocationOption(
    name: String,
    subtitle: String,
    isCurrentLocation: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NimbusCardBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isCurrentLocation) Icons.Filled.MyLocation else Icons.Filled.LocationOn,
            contentDescription = null,
            tint = if (isCurrentLocation) NimbusBlueAccent else NimbusTextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = NimbusTextPrimary,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextTertiary,
                )
            }
        }
    }
}
