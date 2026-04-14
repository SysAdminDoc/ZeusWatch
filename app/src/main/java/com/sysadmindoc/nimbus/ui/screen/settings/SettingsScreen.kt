package com.sysadmindoc.nimbus.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.model.IconPack
import com.sysadmindoc.nimbus.data.repository.*
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCustomAlerts: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = NimbusSettings())
    // Track permission reactively — if the user grants POST_NOTIFICATIONS from
    // system Settings and returns to the app, the banner should disappear
    // without requiring some other recomposition to fire first.
    var notificationsPermissionGranted by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsPermissionGranted = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pendingNotificationEnableAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingAction = pendingNotificationEnableAction
        pendingNotificationEnableAction = null
        notificationsPermissionGranted = granted
        if (granted) {
            pendingAction?.invoke()
        }
    }

    val enableNotificationsIfPermitted: (() -> Unit) -> Unit = { onGranted ->
        if (hasNotificationPermission(context)) {
            onGranted()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotificationEnableAction = onGranted
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onGranted()
        }
    }

    val availableIconPacks by viewModel.availableIconPacks.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onNavigateToCustomAlerts = onNavigateToCustomAlerts,
        notificationsPermissionGranted = notificationsPermissionGranted,
        availableIconPacks = availableIconPacks,
        onTempUnit = { viewModel.setTempUnit(it) },
        onWindUnit = { viewModel.setWindUnit(it) },
        onPressureUnit = { viewModel.setPressureUnit(it) },
        onPrecipUnit = { viewModel.setPrecipUnit(it) },
        onTimeFormat = { viewModel.setTimeFormat(it) },
        onVisibilityUnit = { viewModel.setVisibilityUnit(it) },
        onParticlesEnabled = { viewModel.setParticlesEnabled(it) },
        onAlertNotificationsEnabled = { enabled ->
            if (enabled) {
                enableNotificationsIfPermitted { viewModel.setAlertNotificationsEnabled(true) }
            } else {
                viewModel.setAlertNotificationsEnabled(false)
            }
        },
        onAlertMinSeverity = { viewModel.setAlertMinSeverity(it) },
        onAlertCheckAllLocations = { viewModel.setAlertCheckAllLocations(it) },
        onAlertSourcePref = { viewModel.setAlertSourcePref(it) },
        // Display
        onRadarProvider = { viewModel.setRadarProvider(it) },
        onIconStyle = { viewModel.setIconStyle(it) },
        onCustomIconPackId = { viewModel.setCustomIconPackId(it) },
        onThemeMode = { viewModel.setThemeMode(it) },
        onSummaryStyle = { viewModel.setSummaryStyle(it) },
        // Card config
        onCardEnabled = { card, enabled -> viewModel.setCardEnabled(card, enabled) },
        onCardOrder = { viewModel.setCardOrder(it) },
        onResetCardPreferences = { viewModel.resetCardPreferences() },
        // Notifications
        onPersistentWeatherNotif = { enabled ->
            if (enabled) {
                enableNotificationsIfPermitted { viewModel.setPersistentWeatherNotif(true) }
            } else {
                viewModel.setPersistentWeatherNotif(false)
            }
        },
        onNowcastingAlerts = { enabled ->
            if (enabled) {
                enableNotificationsIfPermitted { viewModel.setNowcastingAlerts(true) }
            } else {
                viewModel.setNowcastingAlerts(false)
            }
        },
        onDrivingAlerts = { viewModel.setDrivingAlerts(it) },
        onHealthAlertsEnabled = { viewModel.setHealthAlertsEnabled(it) },
        // Data display
        onShowSnowfall = { viewModel.setShowSnowfall(it) },
        onShowCape = { viewModel.setShowCape(it) },
        onShowSunshineDuration = { viewModel.setShowSunshineDuration(it) },
        onShowGoldenHour = { viewModel.setShowGoldenHour(it) },
        onShowBeaufortColors = { viewModel.setShowBeaufortColors(it) },
        onShowOutdoorScore = { viewModel.setShowOutdoorScore(it) },
        onShowYesterdayComparison = { viewModel.setShowYesterdayComparison(it) },
        onHourlyForecastHours = { viewModel.setHourlyForecastHours(it) },
        // Health
        onMigraineAlerts = { viewModel.setMigraineAlerts(it) },
        onMigrainePressureThreshold = { viewModel.setMigrainePressureThreshold(it) },
        // Haptics
        onHapticFeedbackForAlerts = { viewModel.setHapticFeedbackForAlerts(it) },
        onCacheTtlMinutes = { viewModel.setCacheTtlMinutes(it) },
        // Data sources
        onSourceForecast = { viewModel.setSourceForecast(it) },
        onSourceForecastFallback = { viewModel.setSourceForecastFallback(it) },
        onSourceAlerts = { viewModel.setSourceAlerts(it) },
        onSourceAlertsFallback = { viewModel.setSourceAlertsFallback(it) },
        onSourceAirQuality = { viewModel.setSourceAirQuality(it) },
        onSourceMinutely = { viewModel.setSourceMinutely(it) },
        onOwmApiKey = { viewModel.setOwmApiKey(it) },
        onPirateWeatherApiKey = { viewModel.setPirateWeatherApiKey(it) },
    )
}

private enum class SettingsCategory(val label: String, val summary: String) {
    APPEARANCE("Appearance", "Theme, icons, radar, motion"),
    FORECAST("Forecast", "Cards, units, detail density"),
    ALERTS("Alerts", "Notifications, thresholds, haptics"),
    ADVANCED("Advanced", "Sources, cache, app info"),
}

@Composable
internal fun SettingsContent(
    settings: NimbusSettings,
    onBack: () -> Unit,
    onNavigateToCustomAlerts: () -> Unit = {},
    notificationsPermissionGranted: Boolean = true,
    onTempUnit: (TempUnit) -> Unit = {},
    onWindUnit: (WindUnit) -> Unit = {},
    onPressureUnit: (PressureUnit) -> Unit = {},
    onPrecipUnit: (PrecipUnit) -> Unit = {},
    onTimeFormat: (TimeFormat) -> Unit = {},
    onVisibilityUnit: (VisibilityUnit) -> Unit = {},
    onParticlesEnabled: (Boolean) -> Unit = {},
    onAlertNotificationsEnabled: (Boolean) -> Unit = {},
    onAlertMinSeverity: (AlertMinSeverity) -> Unit = {},
    onAlertCheckAllLocations: (Boolean) -> Unit = {},
    onAlertSourcePref: (AlertSourcePreference) -> Unit = {},
    // Display
    onRadarProvider: (RadarProvider) -> Unit = {},
    onIconStyle: (IconStyle) -> Unit = {},
    onCustomIconPackId: (String) -> Unit = {},
    availableIconPacks: List<IconPack> = emptyList(),
    onThemeMode: (ThemeMode) -> Unit = {},
    onSummaryStyle: (SummaryStyle) -> Unit = {},
    // Card config
    onCardEnabled: (CardType, Boolean) -> Unit = { _, _ -> },
    onCardOrder: (List<CardType>) -> Unit = {},
    onResetCardPreferences: () -> Unit = {},
    // Notifications
    onPersistentWeatherNotif: (Boolean) -> Unit = {},
    onNowcastingAlerts: (Boolean) -> Unit = {},
    onDrivingAlerts: (Boolean) -> Unit = {},
    onHealthAlertsEnabled: (Boolean) -> Unit = {},
    // Data display
    onShowSnowfall: (Boolean) -> Unit = {},
    onShowCape: (Boolean) -> Unit = {},
    onShowSunshineDuration: (Boolean) -> Unit = {},
    onShowGoldenHour: (Boolean) -> Unit = {},
    onShowBeaufortColors: (Boolean) -> Unit = {},
    onShowOutdoorScore: (Boolean) -> Unit = {},
    onShowYesterdayComparison: (Boolean) -> Unit = {},
    onHourlyForecastHours: (Int) -> Unit = {},
    // Health
    onMigraineAlerts: (Boolean) -> Unit = {},
    onMigrainePressureThreshold: (Double) -> Unit = {},
    // Haptics
    onHapticFeedbackForAlerts: (Boolean) -> Unit = {},
    onCacheTtlMinutes: (Int) -> Unit = {},
    // Data sources
    onSourceForecast: (WeatherSourceProvider) -> Unit = {},
    onSourceForecastFallback: (WeatherSourceProvider?) -> Unit = {},
    onSourceAlerts: (WeatherSourceProvider) -> Unit = {},
    onSourceAlertsFallback: (WeatherSourceProvider?) -> Unit = {},
    onSourceAirQuality: (WeatherSourceProvider) -> Unit = {},
    onSourceMinutely: (WeatherSourceProvider) -> Unit = {},
    onOwmApiKey: (String) -> Unit = {},
    onPirateWeatherApiKey: (String) -> Unit = {},
) {
    PredictiveBackScaffold(onBack = onBack) {
        val scrollState = rememberScrollState()
        var selectedCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }

        LaunchedEffect(selectedCategory) {
            scrollState.scrollTo(0)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NimbusBackgroundGradient)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(scrollState),
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NimbusGlassTop.copy(alpha = 0.76f),
                                NimbusGlassBottom,
                            ),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NimbusTextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "Tune the forecast, visuals, alerts, and data sources to your style.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsCategoryPicker(
            selectedCategory = selectedCategory,
            onSelectedCategory = { selectedCategory = it },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Display ──────────────────────────────────────────
        if (selectedCategory == SettingsCategory.APPEARANCE) {
        SettingSection("Display") {
            Text(
                text = "Radar Provider",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            RadarProvider.entries.forEach { provider ->
                SettingRadio(
                    label = provider.label,
                    selected = settings.radarProvider == provider,
                    onClick = { onRadarProvider(provider) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Icon Style",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            IconStyle.entries.forEach { style ->
                SettingRadio(
                    label = style.label,
                    selected = settings.iconStyle == style,
                    onClick = { onIconStyle(style) },
                )
            }
            // Show icon pack picker when CUSTOM is selected
            if (settings.iconStyle == IconStyle.CUSTOM) {
                IconPackSelector(
                    packs = availableIconPacks,
                    selectedPackId = settings.customIconPackId,
                    onPackSelected = onCustomIconPackId,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            ThemeMode.entries.forEach { mode ->
                SettingRadio(
                    label = mode.label,
                    selected = settings.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Weather Summary Style",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            SummaryStyle.entries.forEach { style ->
                SettingRadio(
                    label = style.label,
                    selected = settings.summaryStyle == style,
                    onClick = { onSummaryStyle(style) },
                )
            }
        }
        }

        // ── Cards (ordered, with move up/down) ─────────────
        if (selectedCategory == SettingsCategory.FORECAST) {
        SettingSection("Home Cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Turn cards on or off, then nudge favorites higher.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = NimbusBlueAccent,
                    modifier = Modifier.clickable(onClick = onResetCardPreferences),
                )
            }
            settings.cardOrder.forEachIndexed { index, card ->
                val enabled = card.name !in settings.disabledCards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCardEnabled(card, !enabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row {
                        IconButton(
                            onClick = {
                                val newOrder = settings.cardOrder.toMutableList()
                                val item = newOrder.removeAt(index)
                                newOrder.add(index - 1, item)
                                onCardOrder(newOrder)
                            },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move ${card.label} up",
                                tint = if (index > 0) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.4f),
                            )
                        }
                        IconButton(
                            onClick = {
                                val newOrder = settings.cardOrder.toMutableList()
                                val item = newOrder.removeAt(index)
                                newOrder.add(index + 1, item)
                                onCardOrder(newOrder)
                            },
                            enabled = index < settings.cardOrder.lastIndex,
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move ${card.label} down",
                                tint = if (index < settings.cardOrder.lastIndex) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.4f),
                            )
                        }
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) NimbusTextPrimary else NimbusTextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onCardEnabled(card, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NimbusBlueAccent,
                            checkedTrackColor = NimbusBlueAccent.copy(alpha = 0.3f),
                            uncheckedThumbColor = NimbusTextTertiary,
                            uncheckedTrackColor = NimbusCardBg,
                        ),
                    )
                }
            }
        }

        // ── Units ────────────────────────────────────────────

        // Temperature
        SettingSection("Temperature") {
            TempUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    sublabel = unit.symbol,
                    selected = settings.tempUnit == unit,
                    onClick = { onTempUnit(unit) },
                )
            }
        }

        // Wind Speed
        SettingSection("Wind Speed") {
            WindUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.windUnit == unit,
                    onClick = { onWindUnit(unit) },
                )
            }
        }

        // Pressure
        SettingSection("Pressure") {
            PressureUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.pressureUnit == unit,
                    onClick = { onPressureUnit(unit) },
                )
            }
        }

        // Precipitation
        SettingSection("Precipitation") {
            PrecipUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.precipUnit == unit,
                    onClick = { onPrecipUnit(unit) },
                )
            }
        }

        // Time Format
        SettingSection("Time Format") {
            TimeFormat.entries.forEach { format ->
                SettingRadio(
                    label = format.label,
                    selected = settings.timeFormat == format,
                    onClick = { onTimeFormat(format) },
                )
            }
        }

        // Visibility
        SettingSection("Visibility") {
            VisibilityUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.visibilityUnit == unit,
                    onClick = { onVisibilityUnit(unit) },
                )
            }
        }

        // ── Notifications ────────────────────────────────────
        }
        if (selectedCategory == SettingsCategory.ALERTS) {
        SettingSection("Notifications") {
            if (!notificationsPermissionGranted) {
                PermissionNoticeCard(
                    title = "Notification Permission Off",
                    message = "Android notifications are blocked for ZeusWatch. Turn them on to receive severe weather alerts and the persistent forecast notification.",
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            SettingToggle(
                label = "Alert Notifications",
                sublabel = "Background checks for severe weather alerts",
                checked = settings.alertNotificationsEnabled,
                onCheckedChange = { onAlertNotificationsEnabled(it) },
            )
            if (settings.alertNotificationsEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Minimum severity",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
                AlertMinSeverity.entries.forEach { severity ->
                    SettingRadio(
                        label = severity.label,
                        selected = settings.alertMinSeverity == severity,
                        onClick = { onAlertMinSeverity(severity) },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SettingToggle(
                    label = "Monitor All Saved Locations",
                    sublabel = "Check alerts for all your locations, not just the current one",
                    checked = settings.alertCheckAllLocations,
                    onCheckedChange = { onAlertCheckAllLocations(it) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Alert source",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            AlertSourcePreference.entries.forEach { pref ->
                SettingRadio(
                    label = pref.label,
                    selected = settings.alertSourcePref == pref,
                    onClick = { onAlertSourcePref(pref) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SettingToggle(
                label = "Persistent Weather Notification",
                sublabel = "Always-on notification showing current conditions",
                checked = settings.persistentWeatherNotif,
                onCheckedChange = { onPersistentWeatherNotif(it) },
            )
            SettingToggle(
                label = "Nowcasting Alerts",
                sublabel = "Alert when rain is approaching",
                checked = settings.nowcastingAlerts,
                onCheckedChange = { onNowcastingAlerts(it) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NimbusCardBg)
                    .clickable(onClick = onNavigateToCustomAlerts)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Custom Alert Rules",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NimbusTextPrimary,
                    )
                    Text(
                        "Set thresholds for temperature, wind, rain, UV",
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = NimbusTextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SettingToggle(
                label = "Driving Condition Alerts",
                sublabel = "Alerts for hazardous driving conditions",
                checked = settings.drivingAlerts,
                onCheckedChange = { onDrivingAlerts(it) },
            )
            SettingToggle(
                label = "Health Alerts",
                sublabel = "Alerts for health-related weather conditions",
                checked = settings.healthAlertsEnabled,
                onCheckedChange = { onHealthAlertsEnabled(it) },
            )
        }

        // ── Data Display ─────────────────────────────────────
        }
        if (selectedCategory == SettingsCategory.FORECAST) {
        SettingSection("Data Display") {
            SettingToggle(
                label = "Yesterday Comparison",
                sublabel = "Show warmer/cooler trend in the hero header",
                checked = settings.showYesterdayComparison,
                onCheckedChange = { onShowYesterdayComparison(it) },
            )
            SettingToggle(
                label = "Outdoor Activity Score",
                sublabel = "Show the blended comfort score card",
                checked = settings.showOutdoorScore,
                onCheckedChange = { onShowOutdoorScore(it) },
            )
            SettingToggle(
                label = "Snowfall Insights",
                sublabel = "Show snowfall rate and depth cards when available",
                checked = settings.showSnowfall,
                onCheckedChange = { onShowSnowfall(it) },
            )
            SettingToggle(
                label = "Storm Potential",
                sublabel = "Show thunderstorm instability and CAPE cards",
                checked = settings.showCape,
                onCheckedChange = { onShowCape(it) },
            )
            SettingToggle(
                label = "Golden Hour Times",
                sublabel = "Include photography-friendly sunrise and sunset windows",
                checked = settings.showGoldenHour,
                onCheckedChange = { onShowGoldenHour(it) },
            )
            SettingToggle(
                label = "Sunshine Duration",
                sublabel = "Show total sunshine progress when forecast data supports it",
                checked = settings.showSunshineDuration,
                onCheckedChange = { onShowSunshineDuration(it) },
            )
            SettingToggle(
                label = "Show Beaufort Wind Colors",
                checked = settings.showBeaufortColors,
                onCheckedChange = { onShowBeaufortColors(it) },
            )

            // Hourly forecast range
            Text(
                text = "Hourly forecast range",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
            )
            listOf(48, 72).forEach { hours ->
                SettingRadio(
                    label = "${hours}h",
                    sublabel = if (hours == 72) "More data, slightly larger API response" else null,
                    selected = settings.hourlyForecastHours == hours,
                    onClick = { onHourlyForecastHours(hours) },
                )
            }
        }
        }

        // ── Health ───────────────────────────────────────────
        if (selectedCategory == SettingsCategory.ALERTS && settings.healthAlertsEnabled) {
            SettingSection("Health") {
                SettingToggle(
                    label = "Migraine Alerts",
                    sublabel = "Alert on rapid pressure changes",
                    checked = settings.migraineAlerts,
                    onCheckedChange = { onMigraineAlerts(it) },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pressure change threshold",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
                listOf(3.0, 5.0, 7.0, 10.0).forEach { threshold ->
                    SettingRadio(
                        label = "$threshold hPa/3h",
                        sublabel = when (threshold) {
                            3.0 -> "Very sensitive"
                            5.0 -> "Moderate (default)"
                            7.0 -> "Less sensitive"
                            10.0 -> "Only major changes"
                            else -> null
                        },
                        selected = settings.migrainePressureThreshold == threshold,
                        onClick = { onMigrainePressureThreshold(threshold) },
                    )
                }
            }
        }

        // ── Accessibility ────────────────────────────────────
        if (selectedCategory == SettingsCategory.ALERTS) {
        SettingSection("Accessibility") {
            SettingToggle(
                label = "Haptic Feedback for Alerts",
                sublabel = "Vibration feedback when alerts are shown",
                checked = settings.hapticFeedbackForAlerts,
                onCheckedChange = { onHapticFeedbackForAlerts(it) },
            )
        }
        }

        // ── Visual Effects ───────────────────────────────────
        if (selectedCategory == SettingsCategory.APPEARANCE) {
        SettingSection("Visual Effects") {
            SettingToggle(
                label = "Weather Particles",
                sublabel = "Rain, snow, and sun ray animations",
                checked = settings.particlesEnabled,
                onCheckedChange = { onParticlesEnabled(it) },
            )
        }
        }

        // ── Data Sources ─────────────────────────────────────────
        if (selectedCategory == SettingsCategory.ADVANCED) {
        SettingSection("Data Sources", initiallyExpanded = false) {
            val sourceConfig = settings.sourceConfig

            // Forecast source
            SourceDropdown(
                label = "Forecast Source",
                selected = sourceConfig.forecast,
                options = WeatherSourceProvider.forType(WeatherDataType.FORECAST),
                onSelected = onSourceForecast,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Forecast fallback
            SourceDropdownNullable(
                label = "Forecast Fallback",
                selected = sourceConfig.forecastFallback,
                options = WeatherSourceProvider.forType(WeatherDataType.FORECAST)
                    .filter { it != sourceConfig.forecast },
                onSelected = onSourceForecastFallback,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Alert source
            SourceDropdown(
                label = "Alert Source",
                selected = sourceConfig.alerts,
                options = WeatherSourceProvider.forType(WeatherDataType.ALERTS),
                onSelected = onSourceAlerts,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Alert fallback
            SourceDropdownNullable(
                label = "Alert Fallback",
                selected = sourceConfig.alertsFallback,
                options = WeatherSourceProvider.forType(WeatherDataType.ALERTS)
                    .filter { it != sourceConfig.alerts },
                onSelected = onSourceAlertsFallback,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Air quality source
            SourceDropdown(
                label = "Air Quality Source",
                selected = sourceConfig.airQuality,
                options = WeatherSourceProvider.forType(WeatherDataType.AIR_QUALITY),
                onSelected = onSourceAirQuality,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Minutely source
            SourceDropdown(
                label = "Minutely Precipitation Source",
                selected = sourceConfig.minutely,
                options = WeatherSourceProvider.forType(WeatherDataType.MINUTELY),
                onSelected = onSourceMinutely,
            )

            // API key fields — shown conditionally
            val needsOwmKey = sourceConfig.forecast == WeatherSourceProvider.OPEN_WEATHER_MAP ||
                sourceConfig.forecastFallback == WeatherSourceProvider.OPEN_WEATHER_MAP ||
                sourceConfig.alerts == WeatherSourceProvider.OPEN_WEATHER_MAP ||
                sourceConfig.alertsFallback == WeatherSourceProvider.OPEN_WEATHER_MAP ||
                sourceConfig.airQuality == WeatherSourceProvider.OPEN_WEATHER_MAP

            val needsPirateKey = sourceConfig.forecast == WeatherSourceProvider.PIRATE_WEATHER ||
                sourceConfig.forecastFallback == WeatherSourceProvider.PIRATE_WEATHER

            if (needsOwmKey || needsPirateKey) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "API Keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            if (needsOwmKey) {
                ApiKeyField(
                    label = "OpenWeatherMap API Key",
                    value = settings.owmApiKey,
                    onValueChange = onOwmApiKey,
                )
            }

            if (needsPirateKey) {
                ApiKeyField(
                    label = "Pirate Weather API Key",
                    value = settings.pirateWeatherApiKey,
                    onValueChange = onPirateWeatherApiKey,
                )
            }
        }

        // ── Advanced ───────────────────────────────────────────
        SettingSection("Advanced", initiallyExpanded = false) {
            Text(
                "Cache Duration",
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            listOf(15, 30, 60, 120).forEach { minutes ->
                val label = if (minutes < 60) "${minutes} minutes" else "${minutes / 60} hour${if (minutes > 60) "s" else ""}"
                SettingRadio(
                    label = label,
                    selected = settings.cacheTtlMinutes == minutes,
                    onClick = { onCacheTtlMinutes(minutes) },
                )
            }
        }

        // ── About ────────────────────────────────────────────
        SettingSection("About") {
            SettingInfo("Version", com.sysadmindoc.nimbus.BuildConfig.VERSION_NAME)
            SettingInfo("Data Sources", "Open-Meteo, NWS, and more")
            SettingInfo("License", "LGPL-3.0")
        }
        }

        Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCategoryPicker(
    selectedCategory: SettingsCategory,
    onSelectedCategory: (SettingsCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isSelected) NimbusBlueAccent.copy(alpha = 0.14f)
                        else NimbusCardBg,
                    )
                    .border(
                        1.dp,
                        if (isSelected) NimbusBlueAccent.copy(alpha = 0.34f) else NimbusCardBorder,
                        RoundedCornerShape(22.dp),
                    )
                    .clickable { onSelectedCategory(category) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) NimbusTextPrimary else NimbusTextSecondary,
                )
                Text(
                    text = category.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.65f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(bottom = if (expanded) 10.dp else 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextPrimary,
            )
            Text(
                text = if (expanded) "\u25B2" else "\u25BC",
                style = MaterialTheme.typography.labelSmall,
                color = NimbusBlueAccent.copy(alpha = 0.8f),
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRadio(
    label: String,
    sublabel: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) NimbusBlueAccent.copy(alpha = 0.14f) else NimbusCardBg)
            .border(1.dp, if (selected) NimbusBlueAccent.copy(alpha = 0.35f) else NimbusCardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = NimbusBlueAccent,
                unselectedColor = NimbusTextTertiary,
            ),
            modifier = Modifier.size(36.dp),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = NimbusTextPrimary)
            if (sublabel != null) {
                Text(text = sublabel, style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
            }
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (checked) NimbusBlueAccent.copy(alpha = 0.12f) else NimbusCardBg)
            .border(1.dp, if (checked) NimbusBlueAccent.copy(alpha = 0.32f) else NimbusCardBorder, RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = NimbusTextPrimary)
            if (sublabel != null) {
                Text(text = sublabel, style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NimbusBlueAccent,
                checkedTrackColor = NimbusBlueAccent.copy(alpha = 0.3f),
                uncheckedThumbColor = NimbusTextTertiary,
                uncheckedTrackColor = NimbusCardBg,
            ),
        )
    }
}

@Composable
private fun SettingInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NimbusCardBg)
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = NimbusTextPrimary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = NimbusTextSecondary)
    }
}

// ── Data Source Composables ─────────────────────────────────────────────

@Composable
private fun SourceDropdown(
    label: String,
    selected: WeatherSourceProvider,
    options: List<WeatherSourceProvider>,
    onSelected: (WeatherSourceProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NimbusCardBg)
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = provider.displayName,
                                style = if (provider == selected) MaterialTheme.typography.bodyLarge
                                else MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            onSelected(provider)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceDropdownNullable(
    label: String,
    selected: WeatherSourceProvider?,
    options: List<WeatherSourceProvider>,
    onSelected: (WeatherSourceProvider?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NimbusCardBg)
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.displayName ?: "None",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected == null) NimbusTextTertiary else NimbusTextPrimary,
                )
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                // "None" option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "None",
                            style = if (selected == null) MaterialTheme.typography.bodyLarge
                            else MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    },
                )
                options.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = provider.displayName,
                                style = if (provider == selected) MaterialTheme.typography.bodyLarge
                                else MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            onSelected(provider)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var text by remember(label) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value, isFocused) {
        if (!isFocused && value != text) {
            text = value
        }
    }

    fun commitValue() {
        val committed = text.trim()
        if (committed != value) {
            onValueChange(committed)
        }
        if (committed != text) {
            text = committed
        }
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Text(
            text = "Saved when you leave the field",
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { updatedText: String -> text = updatedText },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = NimbusTextPrimary),
            cursorBrush = SolidColor(NimbusBlueAccent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    commitValue()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier.onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused) {
                    commitValue()
                }
                isFocused = focusState.isFocused
            },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NimbusCardBg)
                        .border(1.dp, NimbusCardBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Enter API key...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NimbusTextTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun PermissionNoticeCard(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NimbusBlueAccent.copy(alpha = 0.12f))
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = NimbusTextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
    }
}

// ── Icon Pack Selector ────────────────────────────────────────────────

@Composable
private fun IconPackSelector(
    packs: List<IconPack>,
    selectedPackId: String,
    onPackSelected: (String) -> Unit,
) {
    if (packs.isEmpty()) {
        Text(
            text = "No icon packs installed. Place packs in assets/iconpacks/ or install an app with the com.sysadmindoc.nimbus.ICON_PACK intent.",
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextTertiary,
            modifier = Modifier.padding(start = 40.dp, top = 4.dp, end = 16.dp),
        )
        return
    }

    Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
        Text(
            text = "Select Icon Pack",
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        packs.forEach { pack ->
            SettingRadio(
                label = pack.name,
                sublabel = if (pack.author.isNotBlank()) "by ${pack.author}" else null,
                selected = selectedPackId == pack.id,
                onClick = { onPackSelected(pack.id) },
            )
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
