package com.sysadmindoc.nimbus.ui.screen.settings

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.data.model.IconPack
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
        availableIconPacks = viewModel.availableIconPacks,
        onTempUnit = { viewModel.setTempUnit(it) },
        onWindUnit = { viewModel.setWindUnit(it) },
        onPressureUnit = { viewModel.setPressureUnit(it) },
        onPrecipUnit = { viewModel.setPrecipUnit(it) },
        onTimeFormat = { viewModel.setTimeFormat(it) },
        onVisibilityUnit = { viewModel.setVisibilityUnit(it) },
        onParticlesEnabled = { viewModel.setParticlesEnabled(it) },
        onAlertNotificationsEnabled = { viewModel.setAlertNotificationsEnabled(it) },
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

@Composable
internal fun SettingsContent(
    settings: NimbusSettings,
    onBack: () -> Unit,
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

        // ── Health ───────────────────────────────────────────
        if (settings.healthAlertsEnabled) {
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

        // ── Data Sources ─────────────────────────────────────────
        SettingSection("Data Sources") {
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
        SettingSection("Advanced") {
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
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
    var text by remember(value) { mutableStateOf(value) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = NimbusTextPrimary),
            cursorBrush = SolidColor(NimbusBlueAccent),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
