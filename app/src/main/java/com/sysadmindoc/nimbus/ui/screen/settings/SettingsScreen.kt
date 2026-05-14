package com.sysadmindoc.nimbus.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.IconPack
import com.sysadmindoc.nimbus.data.repository.*
import com.sysadmindoc.nimbus.ui.component.InlineNoticeCard
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.ScreenHeader
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

private enum class SettingsCategory(
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int,
) {
    APPEARANCE(R.string.settings_category_appearance, R.string.settings_category_appearance_summary),
    FORECAST(R.string.settings_category_forecast, R.string.settings_category_forecast_summary),
    ALERTS(R.string.settings_category_alerts, R.string.settings_category_alerts_summary),
    ADVANCED(R.string.settings_category_advanced, R.string.settings_category_advanced_summary),
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
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                eyebrow = stringResource(R.string.settings_eyebrow),
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsCategoryPicker(
                selectedCategory = selectedCategory,
                onSelectedCategory = { selectedCategory = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsOverviewCard(
                selectedCategory = selectedCategory,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

        // ── Display ──────────────────────────────────────────
        if (selectedCategory == SettingsCategory.APPEARANCE) {
        SettingSection(
            title = stringResource(R.string.settings_display_title),
            description = stringResource(R.string.settings_display_desc),
        ) {
            Text(
                text = stringResource(R.string.settings_radar_provider),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            RadarProvider.entries.forEach { provider ->
                SettingRadio(
                    label = provider.label,
                    sublabel = provider.summary,
                    selected = settings.radarProvider == provider,
                    onClick = { onRadarProvider(provider) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_radar_hint),
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_icon_style),
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
                text = stringResource(R.string.settings_theme_mode),
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
                text = stringResource(R.string.settings_summary_style),
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
        SettingSection(
            title = stringResource(R.string.settings_home_cards_title),
            description = stringResource(R.string.settings_home_cards_desc),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_home_cards_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.settings_reset),
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
                                contentDescription = stringResource(R.string.settings_move_card_up, card.label),
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
                                contentDescription = stringResource(R.string.settings_move_card_down, card.label),
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
                    val cardToggleDescription = if (enabled) {
                        stringResource(R.string.settings_hide_card, card.label)
                    } else {
                        stringResource(R.string.settings_show_card, card.label)
                    }
                    val cardToggleState = if (enabled) {
                        stringResource(R.string.common_on)
                    } else {
                        stringResource(R.string.common_off)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onCardEnabled(card, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NimbusBlueAccent,
                            checkedTrackColor = NimbusBlueAccent.copy(alpha = 0.3f),
                            uncheckedThumbColor = NimbusTextTertiary,
                            uncheckedTrackColor = NimbusCardBg,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = cardToggleDescription
                            stateDescription = cardToggleState
                        },
                    )
                }
            }
        }

        // ── Units ────────────────────────────────────────────

        // Temperature
        SettingSection(stringResource(R.string.settings_temperature)) {
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
        SettingSection(stringResource(R.string.settings_wind_speed)) {
            WindUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.windUnit == unit,
                    onClick = { onWindUnit(unit) },
                )
            }
        }

        // Pressure
        SettingSection(stringResource(R.string.settings_pressure)) {
            PressureUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.pressureUnit == unit,
                    onClick = { onPressureUnit(unit) },
                )
            }
        }

        // Precipitation
        SettingSection(stringResource(R.string.settings_precipitation)) {
            PrecipUnit.entries.forEach { unit ->
                SettingRadio(
                    label = unit.label,
                    selected = settings.precipUnit == unit,
                    onClick = { onPrecipUnit(unit) },
                )
            }
        }

        // Time Format
        SettingSection(stringResource(R.string.settings_time_format)) {
            TimeFormat.entries.forEach { format ->
                SettingRadio(
                    label = format.label,
                    selected = settings.timeFormat == format,
                    onClick = { onTimeFormat(format) },
                )
            }
        }

        // Visibility
        SettingSection(stringResource(R.string.visibility)) {
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
        SettingSection(
            title = stringResource(R.string.settings_notifications_title),
            description = stringResource(R.string.settings_notifications_desc),
        ) {
            if (!notificationsPermissionGranted) {
                PermissionNoticeCard(
                    title = stringResource(R.string.settings_notification_permission_off),
                    message = stringResource(R.string.settings_notification_permission_message),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            SettingToggle(
                label = stringResource(R.string.settings_alert_notifications),
                sublabel = stringResource(R.string.settings_alert_notifications_desc),
                checked = settings.alertNotificationsEnabled,
                onCheckedChange = { onAlertNotificationsEnabled(it) },
            )
            if (settings.alertNotificationsEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_minimum_severity),
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
                    label = stringResource(R.string.settings_monitor_all_locations),
                    sublabel = stringResource(R.string.settings_monitor_all_locations_desc),
                    checked = settings.alertCheckAllLocations,
                    onCheckedChange = { onAlertCheckAllLocations(it) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_alert_source),
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
                label = stringResource(R.string.settings_persistent_notification),
                sublabel = stringResource(R.string.settings_persistent_notification_desc),
                checked = settings.persistentWeatherNotif,
                onCheckedChange = { onPersistentWeatherNotif(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_nowcasting_alerts),
                sublabel = stringResource(R.string.settings_nowcasting_alerts_desc),
                checked = settings.nowcastingAlerts,
                onCheckedChange = { onNowcastingAlerts(it) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            val customAlertRulesDescription = stringResource(R.string.settings_custom_alert_rules_cd)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NimbusCardBg)
                    .clickable(
                        onClick = onNavigateToCustomAlerts,
                        role = Role.Button,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = customAlertRulesDescription
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_custom_alert_rules),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NimbusTextPrimary,
                    )
                    Text(
                        stringResource(R.string.settings_custom_alert_rules_desc),
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
                label = stringResource(R.string.settings_driving_alerts),
                sublabel = stringResource(R.string.settings_driving_alerts_desc),
                checked = settings.drivingAlerts,
                onCheckedChange = { onDrivingAlerts(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_health_alerts),
                sublabel = stringResource(R.string.settings_health_alerts_desc),
                checked = settings.healthAlertsEnabled,
                onCheckedChange = { onHealthAlertsEnabled(it) },
            )
        }

        // ── Data Display ─────────────────────────────────────
        }
        if (selectedCategory == SettingsCategory.FORECAST) {
        SettingSection(
            title = stringResource(R.string.settings_data_display_title),
            description = stringResource(R.string.settings_data_display_desc),
        ) {
            SettingToggle(
                label = stringResource(R.string.settings_yesterday_comparison),
                sublabel = stringResource(R.string.settings_yesterday_comparison_desc),
                checked = settings.showYesterdayComparison,
                onCheckedChange = { onShowYesterdayComparison(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_outdoor_score),
                sublabel = stringResource(R.string.settings_outdoor_score_desc),
                checked = settings.showOutdoorScore,
                onCheckedChange = { onShowOutdoorScore(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_snowfall_insights),
                sublabel = stringResource(R.string.settings_snowfall_insights_desc),
                checked = settings.showSnowfall,
                onCheckedChange = { onShowSnowfall(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_storm_potential),
                sublabel = stringResource(R.string.settings_storm_potential_desc),
                checked = settings.showCape,
                onCheckedChange = { onShowCape(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_golden_hour_times),
                sublabel = stringResource(R.string.settings_golden_hour_times_desc),
                checked = settings.showGoldenHour,
                onCheckedChange = { onShowGoldenHour(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_sunshine_duration),
                sublabel = stringResource(R.string.settings_sunshine_duration_desc),
                checked = settings.showSunshineDuration,
                onCheckedChange = { onShowSunshineDuration(it) },
            )
            SettingToggle(
                label = stringResource(R.string.settings_beaufort_colors),
                checked = settings.showBeaufortColors,
                onCheckedChange = { onShowBeaufortColors(it) },
            )

            // Hourly forecast range
            Text(
                text = stringResource(R.string.settings_hourly_range),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
            )
            listOf(48, 72).forEach { hours ->
                SettingRadio(
                    label = "${hours}h",
                    sublabel = if (hours == 72) stringResource(R.string.settings_more_data_response) else null,
                    selected = settings.hourlyForecastHours == hours,
                    onClick = { onHourlyForecastHours(hours) },
                )
            }
        }
        }

        // ── Health ───────────────────────────────────────────
        if (selectedCategory == SettingsCategory.ALERTS && settings.healthAlertsEnabled) {
        SettingSection(
            title = stringResource(R.string.settings_health_title),
            description = stringResource(R.string.settings_health_desc),
        ) {
                SettingToggle(
                    label = stringResource(R.string.settings_migraine_alerts),
                    sublabel = stringResource(R.string.settings_migraine_alerts_desc),
                    checked = settings.migraineAlerts,
                    onCheckedChange = { onMigraineAlerts(it) },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_pressure_threshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
                listOf(3.0, 5.0, 7.0, 10.0).forEach { threshold ->
                    SettingRadio(
                        label = "$threshold hPa/3h",
                        sublabel = when (threshold) {
                            3.0 -> stringResource(R.string.settings_very_sensitive)
                            5.0 -> stringResource(R.string.settings_moderate_default)
                            7.0 -> stringResource(R.string.settings_less_sensitive)
                            10.0 -> stringResource(R.string.settings_only_major_changes)
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
        SettingSection(
            title = stringResource(R.string.settings_accessibility_title),
            description = stringResource(R.string.settings_accessibility_desc),
        ) {
            SettingToggle(
                label = stringResource(R.string.settings_haptic_alerts),
                sublabel = stringResource(R.string.settings_haptic_alerts_desc),
                checked = settings.hapticFeedbackForAlerts,
                onCheckedChange = { onHapticFeedbackForAlerts(it) },
            )
        }
        }

        // ── Visual Effects ───────────────────────────────────
        if (selectedCategory == SettingsCategory.APPEARANCE) {
        SettingSection(
            title = stringResource(R.string.settings_visual_effects_title),
            description = stringResource(R.string.settings_visual_effects_desc),
        ) {
            SettingToggle(
                label = stringResource(R.string.settings_weather_particles),
                sublabel = stringResource(R.string.settings_weather_particles_desc),
                checked = settings.particlesEnabled,
                onCheckedChange = { onParticlesEnabled(it) },
            )
        }
        }

        // ── Data Sources ─────────────────────────────────────────
        if (selectedCategory == SettingsCategory.ADVANCED) {
        SettingSection(
            title = stringResource(R.string.settings_data_sources_title),
            description = stringResource(R.string.settings_data_sources_desc),
            initiallyExpanded = false,
        ) {
            val sourceConfig = settings.sourceConfig

            // Forecast source
            SourceDropdown(
                label = stringResource(R.string.settings_forecast_source),
                selected = sourceConfig.forecast,
                options = WeatherSourceProvider.forType(WeatherDataType.FORECAST),
                onSelected = onSourceForecast,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Forecast fallback
            SourceDropdownNullable(
                label = stringResource(R.string.settings_forecast_fallback),
                selected = sourceConfig.forecastFallback,
                options = WeatherSourceProvider.forType(WeatherDataType.FORECAST)
                    .filter { it != sourceConfig.forecast },
                onSelected = onSourceForecastFallback,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Alert source
            SourceDropdown(
                label = stringResource(R.string.settings_alert_source),
                selected = sourceConfig.alerts,
                options = WeatherSourceProvider.forType(WeatherDataType.ALERTS),
                onSelected = onSourceAlerts,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Alert fallback
            SourceDropdownNullable(
                label = stringResource(R.string.settings_alert_fallback),
                selected = sourceConfig.alertsFallback,
                options = WeatherSourceProvider.forType(WeatherDataType.ALERTS)
                    .filter { it != sourceConfig.alerts },
                onSelected = onSourceAlertsFallback,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Air quality source
            SourceDropdown(
                label = stringResource(R.string.settings_air_quality_source),
                selected = sourceConfig.airQuality,
                options = WeatherSourceProvider.forType(WeatherDataType.AIR_QUALITY),
                onSelected = onSourceAirQuality,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Minutely source
            SourceDropdown(
                label = stringResource(R.string.settings_minutely_source),
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
                    text = stringResource(R.string.settings_api_keys),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            if (needsOwmKey) {
                ApiKeyField(
                    label = stringResource(R.string.settings_owm_key),
                    value = settings.owmApiKey,
                    onValueChange = onOwmApiKey,
                )
            }

            if (needsPirateKey) {
                ApiKeyField(
                    label = stringResource(R.string.settings_pirate_key),
                    value = settings.pirateWeatherApiKey,
                    onValueChange = onPirateWeatherApiKey,
                )
            }
        }

        // ── Advanced ───────────────────────────────────────────
        SettingSection(
            title = stringResource(R.string.settings_advanced_title),
            description = stringResource(R.string.settings_advanced_desc),
            initiallyExpanded = false,
        ) {
            Text(
                stringResource(R.string.settings_cache_duration),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            listOf(15, 30, 60, 120).forEach { minutes ->
                val label = when {
                    minutes < 60 -> stringResource(R.string.settings_minutes, minutes)
                    minutes == 60 -> stringResource(R.string.settings_hour, 1)
                    else -> stringResource(R.string.settings_hours, minutes / 60)
                }
                SettingRadio(
                    label = label,
                    selected = settings.cacheTtlMinutes == minutes,
                    onClick = { onCacheTtlMinutes(minutes) },
                )
            }
        }

        // ── About ────────────────────────────────────────────
        SettingSection(
            title = stringResource(R.string.settings_about_title),
            description = stringResource(R.string.settings_about_desc),
        ) {
            SettingInfo(stringResource(R.string.settings_version), com.sysadmindoc.nimbus.BuildConfig.VERSION_NAME)
            SettingInfo(stringResource(R.string.settings_data_sources_title), stringResource(R.string.settings_data_sources_value))
            SettingInfo(stringResource(R.string.settings_license), "LGPL-3.0")
        }
        }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsOverviewCard(
    selectedCategory: SettingsCategory,
    modifier: Modifier = Modifier,
) {
    val selectedLabel = stringResource(selectedCategory.labelRes)
    val selectedSummary = stringResource(selectedCategory.summaryRes)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.78f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NimbusBlueAccent),
            )
            Text(
                text = stringResource(R.string.settings_focused_area),
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextTertiary,
            )
        }
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = NimbusTextPrimary,
        )
        Text(
            text = stringResource(R.string.settings_overview_summary, selectedSummary),
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
        )
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
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            val categoryLabel = stringResource(category.labelRes)
            val categorySummary = stringResource(category.summaryRes)
            Column(
                modifier = Modifier
                    .width(156.dp)
                    .heightIn(min = 88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = if (isSelected) {
                                listOf(
                                    NimbusBlueAccent.copy(alpha = 0.20f),
                                    NimbusGlassTop.copy(alpha = 0.72f),
                                    NimbusGlassBottom,
                                )
                            } else {
                                listOf(
                                    NimbusGlassTop.copy(alpha = 0.58f),
                                    NimbusCardBg,
                                )
                            },
                        ),
                    )
                    .border(
                        1.dp,
                        if (isSelected) NimbusBlueAccent.copy(alpha = 0.44f) else NimbusCardBorder,
                        RoundedCornerShape(10.dp),
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelectedCategory(category) },
                        role = Role.Tab,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.45f),
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) NimbusTextPrimary else NimbusTextSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = categorySummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) NimbusTextSecondary else NimbusTextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    description: String? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val expandedLabel = stringResource(R.string.common_expanded)
    val collapsedLabel = stringResource(R.string.common_collapsed)
    val sectionDescription = if (expanded) {
        stringResource(R.string.settings_collapse_section, title)
    } else {
        stringResource(R.string.settings_expand_section, title)
    }
    val sectionState = if (expanded) expandedLabel else collapsedLabel

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .animateContentSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.65f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { expanded = !expanded },
                    role = Role.Button,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = sectionDescription
                    stateDescription = sectionState
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = NimbusTextPrimary,
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NimbusBlueAccent.copy(alpha = if (expanded) 0.18f else 0.10f))
                    .border(
                        1.dp,
                        NimbusBlueAccent.copy(alpha = if (expanded) 0.28f else 0.16f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NimbusBlueAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
    val selectedLabel = stringResource(R.string.common_selected)
    val notSelectedLabel = stringResource(R.string.common_not_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (selected) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = 0.16f),
                            NimbusGlassTop.copy(alpha = 0.66f),
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.48f),
                            NimbusCardBg,
                        )
                    },
                ),
            )
            .border(1.dp, if (selected) NimbusBlueAccent.copy(alpha = 0.38f) else NimbusCardBorder, RoundedCornerShape(10.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(label, sublabel).joinToString(", ")
                stateDescription = if (selected) selectedLabel else notSelectedLabel
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = NimbusBlueAccent,
                unselectedColor = NimbusTextTertiary,
            ),
            modifier = Modifier
                .size(36.dp)
                .clearAndSetSemantics {},
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
    val onLabel = stringResource(R.string.common_on)
    val offLabel = stringResource(R.string.common_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (checked) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = 0.14f),
                            NimbusGlassTop.copy(alpha = 0.62f),
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.46f),
                            NimbusCardBg,
                        )
                    },
                ),
            )
            .border(1.dp, if (checked) NimbusBlueAccent.copy(alpha = 0.34f) else NimbusCardBorder, RoundedCornerShape(10.dp))
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(label, sublabel).joinToString(", ")
                stateDescription = if (checked) onLabel else offLabel
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NimbusBlueAccent,
                checkedTrackColor = NimbusBlueAccent.copy(alpha = 0.3f),
                uncheckedThumbColor = NimbusTextTertiary,
                uncheckedTrackColor = NimbusCardBg,
            ),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun SettingInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.5f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = NimbusTextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = NimbusTextPrimary)
        }
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
    val expandedLabel = stringResource(R.string.common_expanded)
    val collapsedLabel = stringResource(R.string.common_collapsed)
    val sourceDescription = stringResource(R.string.settings_source_selector_cd, label, selected.displayName)

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
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NimbusGlassTop.copy(alpha = 0.52f),
                                NimbusCardBg,
                            ),
                        ),
                    )
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = { expanded = true },
                        role = Role.Button,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = sourceDescription
                        stateDescription = if (expanded) expandedLabel else collapsedLabel
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NimbusTextSecondary,
                    modifier = Modifier.size(18.dp),
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
    val expandedLabel = stringResource(R.string.common_expanded)
    val collapsedLabel = stringResource(R.string.common_collapsed)
    val noneLabel = stringResource(R.string.settings_none)
    val selectedLabel = selected?.displayName ?: noneLabel
    val sourceDescription = stringResource(R.string.settings_source_selector_cd, label, selectedLabel)

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
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NimbusGlassTop.copy(alpha = 0.52f),
                                NimbusCardBg,
                            ),
                        ),
                    )
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = { expanded = true },
                        role = Role.Button,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = sourceDescription
                        stateDescription = if (expanded) expandedLabel else collapsedLabel
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected == null) NimbusTextTertiary else NimbusTextPrimary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NimbusTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = noneLabel,
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
            text = stringResource(R.string.settings_api_key_saved_hint),
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    NimbusGlassTop.copy(alpha = 0.52f),
                                    NimbusCardBg,
                                ),
                            ),
                        )
                        .border(
                            1.dp,
                            if (isFocused) NimbusBlueAccent.copy(alpha = 0.48f) else NimbusCardBorder,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_enter_api_key),
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
    InlineNoticeCard(
        title = title,
        message = message,
        icon = Icons.Filled.Notifications,
    )
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
            text = stringResource(R.string.settings_no_icon_packs),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextTertiary,
            modifier = Modifier.padding(start = 40.dp, top = 4.dp, end = 16.dp),
        )
        return
    }

    Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_select_icon_pack),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        packs.forEach { pack ->
            SettingRadio(
                label = pack.name,
                sublabel = if (pack.author.isNotBlank()) stringResource(R.string.settings_icon_pack_by, pack.author) else null,
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
