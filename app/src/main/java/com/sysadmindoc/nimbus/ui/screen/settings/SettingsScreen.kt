package com.sysadmindoc.nimbus.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.repository.*
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = NimbusSettings())

    SettingsContent(
        settings = settings,
        onBack = onBack,
        onTempUnit = { viewModel.setTempUnit(it) },
        onWindUnit = { viewModel.setWindUnit(it) },
        onPressureUnit = { viewModel.setPressureUnit(it) },
        onPrecipUnit = { viewModel.setPrecipUnit(it) },
        onTimeFormat = { viewModel.setTimeFormat(it) },
        onParticlesEnabled = { viewModel.setParticlesEnabled(it) },
        onAlertNotificationsEnabled = { viewModel.setAlertNotificationsEnabled(it) },
        onAlertMinSeverity = { viewModel.setAlertMinSeverity(it) },
        onAlertCheckAllLocations = { viewModel.setAlertCheckAllLocations(it) },
        // Display
        onRadarProvider = { viewModel.setRadarProvider(it) },
        onIconStyle = { viewModel.setIconStyle(it) },
        onThemeMode = { viewModel.setThemeMode(it) },
        onSummaryStyle = { viewModel.setSummaryStyle(it) },
        // Card config
        onCardEnabled = { card, enabled -> viewModel.setCardEnabled(card, enabled) },
        // Notifications
        onPersistentWeatherNotif = { viewModel.setPersistentWeatherNotif(it) },
        onNowcastingAlerts = { viewModel.setNowcastingAlerts(it) },
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
        // Health
        onMigraineAlerts = { viewModel.setMigraineAlerts(it) },
        // Haptics
        onHapticFeedbackForAlerts = { viewModel.setHapticFeedbackForAlerts(it) },
    )
}

@Composable
internal fun SettingsContent(
    settings: NimbusSettings,
    onBack: () -> Unit,
    onTempUnit: (TempUnit) -> Unit = {},
    onWindUnit: (WindUnit) -> Unit = {},
    onPressureUnit: (PressureUnit) -> Unit = {},
    onPrecipUnit: (PrecipUnit) -> Unit = {},
    onTimeFormat: (TimeFormat) -> Unit = {},
    onParticlesEnabled: (Boolean) -> Unit = {},
    onAlertNotificationsEnabled: (Boolean) -> Unit = {},
    onAlertMinSeverity: (AlertMinSeverity) -> Unit = {},
    onAlertCheckAllLocations: (Boolean) -> Unit = {},
    // Display
    onRadarProvider: (RadarProvider) -> Unit = {},
    onIconStyle: (IconStyle) -> Unit = {},
    onThemeMode: (ThemeMode) -> Unit = {},
    onSummaryStyle: (SummaryStyle) -> Unit = {},
    // Card config
    onCardEnabled: (CardType, Boolean) -> Unit = { _, _ -> },
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
    // Health
    onMigraineAlerts: (Boolean) -> Unit = {},
    // Haptics
    onHapticFeedbackForAlerts: (Boolean) -> Unit = {},
) {
    PredictiveBackScaffold(onBack = onBack) {
        Column(
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
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NimbusTextPrimary,
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Display ──────────────────────────────────────────
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

        // ── Cards ────────────────────────────────────────────
        SettingSection("Cards") {
            CardType.entries.forEach { card ->
                val enabled = card.name !in settings.disabledCards
                SettingToggle(
                    label = card.label,
                    checked = enabled,
                    onCheckedChange = { onCardEnabled(card, it) },
                )
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

        // ── Notifications ────────────────────────────────────
        SettingSection("Notifications") {
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
        SettingSection("Data Display") {
            SettingToggle(
                label = "Show Snowfall",
                checked = settings.showSnowfall,
                onCheckedChange = { onShowSnowfall(it) },
            )
            SettingToggle(
                label = "Show Severe Weather Potential (CAPE)",
                checked = settings.showCape,
                onCheckedChange = { onShowCape(it) },
            )
            SettingToggle(
                label = "Show Sunshine Duration",
                checked = settings.showSunshineDuration,
                onCheckedChange = { onShowSunshineDuration(it) },
            )
            SettingToggle(
                label = "Show Golden Hour",
                checked = settings.showGoldenHour,
                onCheckedChange = { onShowGoldenHour(it) },
            )
            SettingToggle(
                label = "Show Beaufort Wind Colors",
                checked = settings.showBeaufortColors,
                onCheckedChange = { onShowBeaufortColors(it) },
            )
            SettingToggle(
                label = "Show Outdoor Activity Score",
                checked = settings.showOutdoorScore,
                onCheckedChange = { onShowOutdoorScore(it) },
            )
            SettingToggle(
                label = "Show Yesterday Comparison",
                checked = settings.showYesterdayComparison,
                onCheckedChange = { onShowYesterdayComparison(it) },
            )
        }

        // ── Health ───────────────────────────────────────────
        if (settings.healthAlertsEnabled) {
            SettingSection("Health") {
                SettingToggle(
                    label = "Migraine Alerts",
                    sublabel = "Alert on rapid pressure changes",
                    checked = settings.migraineAlerts,
                    onCheckedChange = { onMigraineAlerts(it) },
                )
                SettingInfo(
                    label = "Migraine Pressure Threshold",
                    value = "${settings.migrainePressureThreshold} hPa/3h",
                )
            }
        }

        // ── Accessibility ────────────────────────────────────
        SettingSection("Accessibility") {
            SettingToggle(
                label = "Haptic Feedback for Alerts",
                sublabel = "Vibration feedback when alerts are shown",
                checked = settings.hapticFeedbackForAlerts,
                onCheckedChange = { onHapticFeedbackForAlerts(it) },
            )
        }

        // ── Visual Effects ───────────────────────────────────
        SettingSection("Visual Effects") {
            SettingToggle(
                label = "Weather Particles",
                sublabel = "Rain, snow, and sun ray animations",
                checked = settings.particlesEnabled,
                onCheckedChange = { onParticlesEnabled(it) },
            )
        }

        // ── About ────────────────────────────────────────────
        SettingSection("About") {
            SettingInfo("Version", com.sysadmindoc.nimbus.BuildConfig.VERSION_NAME)
            SettingInfo("Data Source", "Open-Meteo.com")
            SettingInfo("License", "LGPL-3.0")
        }

        Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = NimbusBlueAccent,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
        HorizontalDivider(
            color = NimbusCardBorder,
            modifier = Modifier.padding(top = 8.dp),
        )
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
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
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
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (sublabel != null) {
                Text(text = sublabel, style = MaterialTheme.typography.bodySmall)
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
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (sublabel != null) {
                Text(text = sublabel, style = MaterialTheme.typography.bodySmall)
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
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
