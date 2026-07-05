package com.sysadmindoc.nimbus.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.sysadmindoc.nimbus.util.displayNameRes
import com.sysadmindoc.nimbus.util.labelRes
import com.sysadmindoc.nimbus.util.summaryRes
import java.util.Locale

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
    availableIconPacks: List<IconPack> = emptyList(),
    providerHealth: ProviderHealthSnapshot = ProviderHealthSnapshot(),
    transferStatus: SettingsTransferStatus? = null,
    transferInProgress: Boolean = false,
    pendingImportPreview: SettingsImportPreview? = null,
    actions: SettingsActions = SettingsActions(),
) {
    PredictiveBackScaffold(onBack = onBack) {
        val scrollState = rememberScrollState()
        // rememberSaveable: survive configuration change / process recreation
        // instead of snapping back to Appearance.
        var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE) }

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

            SettingsCategoryContent(
                selectedCategory = selectedCategory,
                settings = settings,
                notificationsPermissionGranted = notificationsPermissionGranted,
                availableIconPacks = availableIconPacks,
                onNavigateToCustomAlerts = onNavigateToCustomAlerts,
                supportState = SettingsSupportState(
                    providerHealth = providerHealth,
                    transferStatus = transferStatus,
                    transferInProgress = transferInProgress,
                    pendingImportPreview = pendingImportPreview,
                ),
                actions = actions,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

internal data class SettingsActions(
    val onTempUnit: (TempUnit) -> Unit = {},
    val onWindUnit: (WindUnit) -> Unit = {},
    val onPressureUnit: (PressureUnit) -> Unit = {},
    val onPrecipUnit: (PrecipUnit) -> Unit = {},
    val onTimeFormat: (TimeFormat) -> Unit = {},
    val onVisibilityUnit: (VisibilityUnit) -> Unit = {},
    val onParticlesEnabled: (Boolean) -> Unit = {},
    val onAlertNotificationsEnabled: (Boolean) -> Unit = {},
    val onAlertMinSeverity: (AlertMinSeverity) -> Unit = {},
    val onAlertCheckAllLocations: (Boolean) -> Unit = {},
    val onAlertSourcePref: (AlertSourcePreference) -> Unit = {},
    val onRadarProvider: (RadarProvider) -> Unit = {},
    val onIconStyle: (IconStyle) -> Unit = {},
    val onCustomIconPackId: (String) -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onSummaryStyle: (SummaryStyle) -> Unit = {},
    val onCustomSummaryTemplate: (String) -> Unit = {},
    val onCardEnabled: (CardType, Boolean) -> Unit = { _, _ -> },
    val onMoveCard: (CardType, Int) -> Unit = { _, _ -> },
    val onResetCardPreferences: () -> Unit = {},
    val onPersistentWeatherNotif: (Boolean) -> Unit = {},
    val onNowcastingAlerts: (Boolean) -> Unit = {},
    val onDailyBriefingEnabled: (Boolean) -> Unit = {},
    val onDailyBriefingMinutes: (Int) -> Unit = {},
    val onDrivingAlerts: (Boolean) -> Unit = {},
    val onHealthAlertsEnabled: (Boolean) -> Unit = {},
    val onShowSnowfall: (Boolean) -> Unit = {},
    val onShowCape: (Boolean) -> Unit = {},
    val onShowSunshineDuration: (Boolean) -> Unit = {},
    val onShowGoldenHour: (Boolean) -> Unit = {},
    val onShowBeaufortColors: (Boolean) -> Unit = {},
    val onShowOutdoorScore: (Boolean) -> Unit = {},
    val onShowYesterdayComparison: (Boolean) -> Unit = {},
    val onShowForecastAccuracy: (Boolean) -> Unit = {},
    val onShowConfidenceBands: (Boolean) -> Unit = {},
    val onHourlyForecastHours: (Int) -> Unit = {},
    val onMigraineAlerts: (Boolean) -> Unit = {},
    val onMigrainePressureThreshold: (Double) -> Unit = {},
    val onHapticFeedbackForAlerts: (Boolean) -> Unit = {},
    val onAccessibilityLayout: (Boolean) -> Unit = {},
    val onCacheTtlMinutes: (Int) -> Unit = {},
    val onSourceForecast: (WeatherSourceProvider) -> Unit = {},
    val onSourceForecastFallback: (WeatherSourceProvider?) -> Unit = {},
    val onSourceAlerts: (WeatherSourceProvider) -> Unit = {},
    val onSourceAlertsFallback: (WeatherSourceProvider?) -> Unit = {},
    val onSourceAirQuality: (WeatherSourceProvider) -> Unit = {},
    val onSourceMinutely: (WeatherSourceProvider) -> Unit = {},
    val onGadgetbridgeBroadcastEnabled: (Boolean) -> Unit = {},
    val onOwmApiKey: (String) -> Unit = {},
    val onPirateWeatherApiKey: (String) -> Unit = {},
    val onTempestAccessToken: (String) -> Unit = {},
    val onTempestDeviceId: (String) -> Unit = {},
    val onExportSettings: () -> Unit = {},
    val onImportSettings: () -> Unit = {},
    val onExportProviderDiagnostics: () -> Unit = {},
    val onConfirmSettingsImport: () -> Unit = {},
    val onCancelSettingsImport: () -> Unit = {},
    val onClearTransferStatus: () -> Unit = {},
)

private data class SettingsSupportState(
    val providerHealth: ProviderHealthSnapshot,
    val transferStatus: SettingsTransferStatus?,
    val transferInProgress: Boolean,
    val pendingImportPreview: SettingsImportPreview?,
)

@Composable
private fun SettingsCategoryContent(
    selectedCategory: SettingsCategory,
    settings: NimbusSettings,
    notificationsPermissionGranted: Boolean,
    availableIconPacks: List<IconPack>,
    onNavigateToCustomAlerts: () -> Unit,
    supportState: SettingsSupportState,
    actions: SettingsActions,
) {
    when (selectedCategory) {
        SettingsCategory.APPEARANCE -> {
            SettingsDisplaySection(settings, availableIconPacks, actions)
            SettingsVisualEffectsSection(settings, actions)
        }
        SettingsCategory.FORECAST -> {
            SettingsHomeCardsSection(settings, actions)
            SettingsUnitsSections(settings, actions)
            SettingsDataDisplaySection(settings, actions)
        }
        SettingsCategory.ALERTS -> {
            SettingsNotificationsSection(settings, notificationsPermissionGranted, onNavigateToCustomAlerts, actions)
            if (settings.healthAlertsEnabled) {
                SettingsHealthSection(settings, actions)
            }
            SettingsAccessibilitySection(settings, actions)
        }
        SettingsCategory.ADVANCED -> {
            SettingsDataSourcesSection(
                settings,
                supportState.providerHealth,
                supportState.transferInProgress,
                actions,
            )
            SettingsAdvancedSection(
                settings,
                supportState.transferStatus,
                supportState.transferInProgress,
                supportState.pendingImportPreview,
                actions,
            )
            SettingsAboutSection()
            SettingsWidgetTroubleshootSection()
        }
    }
}

@Composable
private fun SettingsDisplaySection(
    settings: NimbusSettings,
    availableIconPacks: List<IconPack>,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_display_title),
        description = stringResource(R.string.settings_display_desc),
    ) {
        Text(stringResource(R.string.settings_radar_provider), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        RadarProvider.entries.forEach { provider ->
            SettingRadio(
                label = stringResource(provider.labelRes),
                sublabel = stringResource(provider.summaryRes),
                selected = settings.radarProvider == provider,
                onClick = { actions.onRadarProvider(provider) },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.settings_radar_hint), style = MaterialTheme.typography.labelSmall, color = NimbusTextTertiary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        SettingsIconStyleControls(settings, availableIconPacks, actions)
        SettingsThemeAndSummaryControls(settings, actions)
    }
}

@Composable
private fun SettingsIconStyleControls(
    settings: NimbusSettings,
    availableIconPacks: List<IconPack>,
    actions: SettingsActions,
) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.settings_icon_style), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
    IconStyle.entries.forEach { style ->
        SettingRadio(
            label = stringResource(style.labelRes),
            selected = settings.iconStyle == style,
            onClick = { actions.onIconStyle(style) },
        )
    }
    if (settings.iconStyle == IconStyle.CUSTOM) {
        IconPackSelector(
            packs = availableIconPacks,
            selectedPackId = settings.customIconPackId,
            onPackSelected = actions.onCustomIconPackId,
        )
    }
}

@Composable
private fun SettingsThemeAndSummaryControls(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
    ThemeMode.entries.forEach { mode ->
        SettingRadio(
            label = stringResource(mode.labelRes),
            selected = settings.themeMode == mode,
            onClick = { actions.onThemeMode(mode) },
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(stringResource(R.string.settings_summary_style), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
    SummaryStyle.entries.forEach { style ->
        SettingRadio(
            label = stringResource(style.labelRes),
            selected = settings.summaryStyle == style,
            onClick = { actions.onSummaryStyle(style) },
        )
    }
    if (settings.summaryStyle == SummaryStyle.CUSTOM_TEMPLATE) {
        Spacer(modifier = Modifier.height(8.dp))
        val templateLabel = stringResource(R.string.settings_custom_template_label)
        Text(
            templateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        var templateText by rememberSaveable(settings.customSummaryTemplate) {
            mutableStateOf(settings.customSummaryTemplate)
        }
        BasicTextField(
            value = templateText,
            onValueChange = { raw ->
                val next = raw.take(MAX_CUSTOM_SUMMARY_TEMPLATE_CHARS)
                templateText = next
                actions.onCustomSummaryTemplate(next)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(1.dp, NimbusTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .semantics { contentDescription = templateLabel }
                .padding(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = NimbusTextPrimary),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_custom_template_help),
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SettingsHomeCardsSection(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_home_cards_title),
        description = stringResource(R.string.settings_home_cards_desc),
    ) {
        SettingsHomeCardsHeader(actions.onResetCardPreferences)
        settings.cardOrder.forEachIndexed { index, card ->
            SettingsHomeCardRow(settings, card, index, actions)
        }
    }
}

@Composable
private fun SettingsHomeCardsHeader(onResetCardPreferences: () -> Unit) {
    val resetLabel = stringResource(R.string.settings_reset)
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
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NimbusBlueAccent.copy(alpha = 0.12f))
                .border(1.dp, NimbusBlueAccent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(
                    onClick = onResetCardPreferences,
                    role = Role.Button,
                )
                .semantics { contentDescription = resetLabel }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                resetLabel,
                style = MaterialTheme.typography.labelLarge,
                color = NimbusBlueAccent,
            )
        }
    }
}

@Composable
private fun SettingsHomeCardRow(
    settings: NimbusSettings,
    card: CardType,
    index: Int,
    actions: SettingsActions,
) {
    val enabled = card.name !in settings.disabledCards
    val cardLabel = stringResource(card.labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (enabled) {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.56f),
                            NimbusCardBg,
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.30f),
                            NimbusCardBg.copy(alpha = 0.72f),
                        )
                    },
                ),
            )
            .border(
                1.dp,
                if (enabled) NimbusCardBorder else NimbusCardBorder.copy(alpha = 0.58f),
                RoundedCornerShape(10.dp),
            )
            .clickable(
                onClick = { actions.onCardEnabled(card, !enabled) },
                role = Role.Switch,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCardMoveButtons(settings.cardOrder, index, card, cardLabel, actions.onMoveCard)
        Spacer(Modifier.width(6.dp))
        Text(
            text = cardLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) NimbusTextPrimary else NimbusTextTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsCardEnabledSwitch(card, cardLabel, enabled, actions.onCardEnabled)
    }
}

@Composable
private fun SettingsCardMoveButtons(
    cardOrder: List<CardType>,
    index: Int,
    card: CardType,
    cardLabel: String,
    onMoveCard: (CardType, Int) -> Unit,
) {
    // Moves are expressed as (card, delta) and applied atomically against the
    // persisted order — two rapid taps each move one step instead of replaying
    // the same move from a stale composition snapshot.
    Row {
        IconButton(
            onClick = { onMoveCard(card, -1) },
            enabled = index > 0,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.settings_move_card_up, cardLabel),
                tint = if (index > 0) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.4f),
            )
        }
        IconButton(
            onClick = { onMoveCard(card, 1) },
            enabled = index < cardOrder.lastIndex,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.settings_move_card_down, cardLabel),
                tint = if (index < cardOrder.lastIndex) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun SettingsCardEnabledSwitch(
    card: CardType,
    cardLabel: String,
    enabled: Boolean,
    onCardEnabled: (CardType, Boolean) -> Unit,
) {
    val cardToggleDescription = if (enabled) {
        stringResource(R.string.settings_hide_card, cardLabel)
    } else {
        stringResource(R.string.settings_show_card, cardLabel)
    }
    val cardToggleState = if (enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off)
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

@Composable
private fun SettingsUnitsSections(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    SettingSection(stringResource(R.string.settings_temperature)) {
        TempUnit.entries.forEach { unit ->
            SettingRadio(stringResource(unit.labelRes), unit.symbol, settings.tempUnit == unit) {
                actions.onTempUnit(unit)
            }
        }
    }
    SettingSection(stringResource(R.string.settings_wind_speed)) {
        WindUnit.entries.forEach { unit ->
            SettingRadio(stringResource(unit.labelRes), selected = settings.windUnit == unit) { actions.onWindUnit(unit) }
        }
    }
    SettingSection(stringResource(R.string.settings_pressure)) {
        PressureUnit.entries.forEach { unit ->
            SettingRadio(stringResource(unit.labelRes), selected = settings.pressureUnit == unit) { actions.onPressureUnit(unit) }
        }
    }
    SettingSection(stringResource(R.string.settings_precipitation)) {
        PrecipUnit.entries.forEach { unit ->
            SettingRadio(stringResource(unit.labelRes), selected = settings.precipUnit == unit) { actions.onPrecipUnit(unit) }
        }
    }
    SettingSection(stringResource(R.string.settings_time_format)) {
        TimeFormat.entries.forEach { format ->
            SettingRadio(stringResource(format.labelRes), selected = settings.timeFormat == format) { actions.onTimeFormat(format) }
        }
    }
    SettingSection(stringResource(R.string.visibility)) {
        VisibilityUnit.entries.forEach { unit ->
            SettingRadio(stringResource(unit.labelRes), selected = settings.visibilityUnit == unit) { actions.onVisibilityUnit(unit) }
        }
    }
}

@Composable
private fun SettingsNotificationsSection(
    settings: NimbusSettings,
    notificationsPermissionGranted: Boolean,
    onNavigateToCustomAlerts: () -> Unit,
    actions: SettingsActions,
) {
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
        SettingToggle(stringResource(R.string.settings_alert_notifications), stringResource(R.string.settings_alert_notifications_desc), settings.alertNotificationsEnabled, actions.onAlertNotificationsEnabled)
        if (settings.alertNotificationsEnabled) {
            SettingsAlertNotificationDetails(settings, actions)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.settings_alert_source), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        AlertSourcePreference.entries.forEach { pref ->
            SettingRadio(stringResource(pref.labelRes), selected = settings.alertSourcePref == pref) {
                actions.onAlertSourcePref(pref)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        SettingToggle(stringResource(R.string.settings_persistent_notification), stringResource(R.string.settings_persistent_notification_desc), settings.persistentWeatherNotif, actions.onPersistentWeatherNotif)
        SettingToggle(stringResource(R.string.settings_daily_briefing), stringResource(R.string.settings_daily_briefing_desc), settings.dailyBriefingEnabled, actions.onDailyBriefingEnabled)
        if (settings.dailyBriefingEnabled) {
            DailyBriefingTimeSetting(settings, actions.onDailyBriefingMinutes)
        }
        SettingToggle(stringResource(R.string.settings_nowcasting_alerts), stringResource(R.string.settings_nowcasting_alerts_desc), settings.nowcastingAlerts, actions.onNowcastingAlerts)
        Spacer(modifier = Modifier.height(4.dp))
        CustomAlertRulesRow(onNavigateToCustomAlerts)
        Spacer(modifier = Modifier.height(4.dp))
        SettingToggle(stringResource(R.string.settings_driving_alerts), stringResource(R.string.settings_driving_alerts_desc), settings.drivingAlerts, actions.onDrivingAlerts)
        SettingToggle(stringResource(R.string.settings_health_alerts), stringResource(R.string.settings_health_alerts_desc), settings.healthAlertsEnabled, actions.onHealthAlertsEnabled)
    }
}

@Composable
private fun SettingsAlertNotificationDetails(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(stringResource(R.string.settings_minimum_severity), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
    AlertMinSeverity.entries.forEach { severity ->
        SettingRadio(stringResource(severity.labelRes), selected = settings.alertMinSeverity == severity) {
            actions.onAlertMinSeverity(severity)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    SettingToggle(
        label = stringResource(R.string.settings_monitor_all_locations),
        sublabel = stringResource(R.string.settings_monitor_all_locations_desc),
        checked = settings.alertCheckAllLocations,
        onCheckedChange = actions.onAlertCheckAllLocations,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DailyBriefingTimeSetting(
    settings: NimbusSettings,
    onTimeSelected: (Int) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val formattedTime = formatBriefingTime(settings.dailyBriefingMinutes, settings.timeFormat)
    SettingValueButton(
        label = stringResource(R.string.settings_daily_briefing_time),
        sublabel = stringResource(R.string.settings_daily_briefing_time_desc),
        value = formattedTime,
        onClick = { showPicker = true },
    )
    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.dailyBriefingMinutes / 60,
            initialMinute = settings.dailyBriefingMinutes % 60,
            is24Hour = settings.timeFormat == TimeFormat.TWENTY_FOUR_HOUR,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = {
                Text(stringResource(R.string.settings_daily_briefing_picker_title))
            },
            text = {
                TimePicker(state = pickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(pickerState.hour * 60 + pickerState.minute)
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun CustomAlertRulesRow(onNavigateToCustomAlerts: () -> Unit) {
    val customAlertRulesDescription = stringResource(R.string.settings_custom_alert_rules_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NimbusCardBg)
            .clickable(onClick = onNavigateToCustomAlerts, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = customAlertRulesDescription }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_custom_alert_rules), style = MaterialTheme.typography.bodyMedium, color = NimbusTextPrimary)
            Text(stringResource(R.string.settings_custom_alert_rules_desc), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = NimbusTextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDataDisplaySection(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_data_display_title),
        description = stringResource(R.string.settings_data_display_desc),
    ) {
        SettingToggle(stringResource(R.string.settings_yesterday_comparison), stringResource(R.string.settings_yesterday_comparison_desc), settings.showYesterdayComparison, actions.onShowYesterdayComparison)
        SettingToggle(stringResource(R.string.settings_show_forecast_accuracy), stringResource(R.string.settings_show_forecast_accuracy_desc), settings.showForecastAccuracy, actions.onShowForecastAccuracy)
        SettingToggle(stringResource(R.string.settings_show_confidence_bands), stringResource(R.string.settings_show_confidence_bands_desc), settings.showConfidenceBands, actions.onShowConfidenceBands)
        SettingToggle(stringResource(R.string.settings_outdoor_score), stringResource(R.string.settings_outdoor_score_desc), settings.showOutdoorScore, actions.onShowOutdoorScore)
        SettingToggle(stringResource(R.string.settings_snowfall_insights), stringResource(R.string.settings_snowfall_insights_desc), settings.showSnowfall, actions.onShowSnowfall)
        SettingToggle(stringResource(R.string.settings_storm_potential), stringResource(R.string.settings_storm_potential_desc), settings.showCape, actions.onShowCape)
        SettingToggle(stringResource(R.string.settings_golden_hour_times), stringResource(R.string.settings_golden_hour_times_desc), settings.showGoldenHour, actions.onShowGoldenHour)
        SettingToggle(stringResource(R.string.settings_sunshine_duration), stringResource(R.string.settings_sunshine_duration_desc), settings.showSunshineDuration, actions.onShowSunshineDuration)
        SettingToggle(stringResource(R.string.settings_beaufort_colors), checked = settings.showBeaufortColors, onCheckedChange = actions.onShowBeaufortColors)
        Text(stringResource(R.string.settings_hourly_range), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
        listOf(48, 72).forEach { hours ->
            SettingRadio(
                label = "${hours}h",
                sublabel = if (hours == 72) stringResource(R.string.settings_more_data_response) else null,
                selected = settings.hourlyForecastHours == hours,
                onClick = { actions.onHourlyForecastHours(hours) },
            )
        }
    }
}

@Composable
private fun SettingsHealthSection(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_health_title),
        description = stringResource(R.string.settings_health_desc),
    ) {
        SettingToggle(stringResource(R.string.settings_migraine_alerts), stringResource(R.string.settings_migraine_alerts_desc), settings.migraineAlerts, actions.onMigraineAlerts)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.settings_pressure_threshold), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        listOf(3.0, 5.0, 7.0, 10.0).forEach { threshold ->
            SettingRadio(
                label = "$threshold hPa/3h",
                sublabel = pressureThresholdSublabel(threshold),
                selected = settings.migrainePressureThreshold == threshold,
                onClick = { actions.onMigrainePressureThreshold(threshold) },
            )
        }
    }
}

@Composable
private fun pressureThresholdSublabel(threshold: Double): String? = when (threshold) {
    3.0 -> stringResource(R.string.settings_very_sensitive)
    5.0 -> stringResource(R.string.settings_moderate_default)
    7.0 -> stringResource(R.string.settings_less_sensitive)
    10.0 -> stringResource(R.string.settings_only_major_changes)
    else -> null
}

@Composable
private fun SettingsAccessibilitySection(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_accessibility_title),
        description = stringResource(R.string.settings_accessibility_desc),
    ) {
        SettingToggle(stringResource(R.string.settings_haptic_alerts), stringResource(R.string.settings_haptic_alerts_desc), settings.hapticFeedbackForAlerts, actions.onHapticFeedbackForAlerts)
        SettingToggle(stringResource(R.string.settings_accessibility_layout), stringResource(R.string.settings_accessibility_layout_desc), settings.accessibilityLayout, actions.onAccessibilityLayout)
    }
}

@Composable
private fun SettingsVisualEffectsSection(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_visual_effects_title),
        description = stringResource(R.string.settings_visual_effects_desc),
    ) {
        SettingToggle(stringResource(R.string.settings_weather_particles), stringResource(R.string.settings_weather_particles_desc), settings.particlesEnabled, actions.onParticlesEnabled)
    }
}

@Composable
private fun SettingsDataSourcesSection(
    settings: NimbusSettings,
    providerHealth: ProviderHealthSnapshot,
    transferInProgress: Boolean,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_data_sources_title),
        description = stringResource(R.string.settings_data_sources_desc),
        initiallyExpanded = false,
    ) {
        val sourceConfig = settings.sourceConfig
        SourceDropdown(stringResource(R.string.settings_forecast_source), sourceConfig.forecast, WeatherSourceProvider.forType(WeatherDataType.FORECAST), actions.onSourceForecast)
        Spacer(modifier = Modifier.height(4.dp))
        SourceDropdownNullable(stringResource(R.string.settings_forecast_fallback), sourceConfig.forecastFallback, WeatherSourceProvider.forType(WeatherDataType.FORECAST).filter { it != sourceConfig.forecast }, actions.onSourceForecastFallback)
        Spacer(modifier = Modifier.height(8.dp))
        SourceDropdown(stringResource(R.string.settings_alert_source), sourceConfig.alerts, WeatherSourceProvider.forType(WeatherDataType.ALERTS), actions.onSourceAlerts)
        Spacer(modifier = Modifier.height(4.dp))
        SourceDropdownNullable(stringResource(R.string.settings_alert_fallback), sourceConfig.alertsFallback, WeatherSourceProvider.forType(WeatherDataType.ALERTS).filter { it != sourceConfig.alerts }, actions.onSourceAlertsFallback)
        Spacer(modifier = Modifier.height(8.dp))
        SourceDropdown(stringResource(R.string.settings_air_quality_source), sourceConfig.airQuality, WeatherSourceProvider.forType(WeatherDataType.AIR_QUALITY), actions.onSourceAirQuality)
        Spacer(modifier = Modifier.height(8.dp))
        SourceDropdown(stringResource(R.string.settings_minutely_source), sourceConfig.minutely, WeatherSourceProvider.forType(WeatherDataType.MINUTELY), actions.onSourceMinutely)
        Spacer(modifier = Modifier.height(12.dp))
        SettingToggle(
            stringResource(R.string.settings_gadgetbridge_broadcast),
            stringResource(R.string.settings_gadgetbridge_broadcast_desc),
            settings.gadgetbridgeBroadcastEnabled,
            actions.onGadgetbridgeBroadcastEnabled,
        )
        SettingsApiKeyFields(settings, actions)
        ProviderHealthPanel(
            snapshot = providerHealth,
            transferInProgress = transferInProgress,
            actions = actions,
        )
    }
}

@Composable
private fun SettingsApiKeyFields(
    settings: NimbusSettings,
    actions: SettingsActions,
) {
    val sourceConfig = settings.sourceConfig
    val needsOwmKey = sourceConfig.forecast == WeatherSourceProvider.OPEN_WEATHER_MAP ||
        sourceConfig.forecastFallback == WeatherSourceProvider.OPEN_WEATHER_MAP ||
        sourceConfig.alerts == WeatherSourceProvider.OPEN_WEATHER_MAP ||
        sourceConfig.alertsFallback == WeatherSourceProvider.OPEN_WEATHER_MAP ||
        sourceConfig.airQuality == WeatherSourceProvider.OPEN_WEATHER_MAP
    // AUTO / ALL_SOURCES alert preferences route through the global Pirate
    // Weather alert adapter, which also needs this key — so the field must be
    // visible even when no forecast source selects Pirate Weather.
    val needsPirateKey = sourceConfig.forecast == WeatherSourceProvider.PIRATE_WEATHER ||
        sourceConfig.forecastFallback == WeatherSourceProvider.PIRATE_WEATHER ||
        settings.alertSourcePref == AlertSourcePreference.AUTO ||
        settings.alertSourcePref == AlertSourcePreference.ALL_SOURCES
    val needsTempestConfig = CardType.PWS_OBSERVATION.name !in settings.disabledCards ||
        settings.tempestAccessToken.isNotBlank() ||
        settings.tempestDeviceId.isNotBlank()

    if (needsOwmKey || needsPirateKey || needsTempestConfig) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.settings_api_keys), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
    if (needsOwmKey) {
        ApiKeyField(stringResource(R.string.settings_owm_key), settings.owmApiKey, actions.onOwmApiKey)
    }
    if (needsPirateKey) {
        ApiKeyField(stringResource(R.string.settings_pirate_key), settings.pirateWeatherApiKey, actions.onPirateWeatherApiKey)
    }
    if (needsTempestConfig) {
        ApiKeyField(
            label = stringResource(R.string.settings_tempest_access_token),
            value = settings.tempestAccessToken,
            onValueChange = actions.onTempestAccessToken,
        )
        ApiKeyField(
            label = stringResource(R.string.settings_tempest_device_id),
            value = settings.tempestDeviceId,
            onValueChange = actions.onTempestDeviceId,
            isSecret = false,
            keyboardType = KeyboardType.Number,
            placeholder = stringResource(R.string.settings_enter_value),
        )
    }
}

@Composable
private fun ProviderHealthPanel(
    snapshot: ProviderHealthSnapshot,
    transferInProgress: Boolean,
    actions: SettingsActions,
) {
    val entries = remember(snapshot.entries) {
        snapshot.entries.sortedWith(
            compareBy<ProviderHealthEntry> { it.type.ordinal }.thenBy { it.provider.name },
        )
    }
    val nowEpochMs = remember { System.currentTimeMillis() }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.settings_provider_health_title),
        style = MaterialTheme.typography.bodySmall,
        color = NimbusTextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
    Text(
        text = stringResource(R.string.settings_provider_health_desc),
        style = MaterialTheme.typography.bodySmall,
        color = NimbusTextTertiary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_provider_health_empty),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                ProviderHealthRow(entry, nowEpochMs)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    SettingsTransferButton(
        label = stringResource(R.string.settings_provider_health_export),
        icon = Icons.Filled.FileUpload,
        enabled = !transferInProgress,
        modifier = Modifier.fillMaxWidth(),
        onClick = actions.onExportProviderDiagnostics,
    )
}

@Composable
private fun ProviderHealthRow(
    entry: ProviderHealthEntry,
    nowEpochMs: Long,
) {
    val typeLabel = providerHealthTypeLabel(entry.type)
    val providerLabel = stringResource(entry.provider.displayNameRes)
    val successLabel = providerHealthRelativeTime(
        entry.lastSuccessEpochMs,
        nowEpochMs,
        emptyRes = R.string.settings_provider_health_no_success,
    )
    val failureLabel = providerHealthRelativeTime(
        entry.lastFailureEpochMs,
        nowEpochMs,
        emptyRes = R.string.settings_provider_health_no_failure,
    )
    val failureClass = entry.lastFailureReason?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.settings_provider_health_none)
    val cacheAge = entry.lastCacheAgeMinutes?.let { cacheDurationLabel(it.toInt()) }
        ?: stringResource(R.string.settings_provider_health_no_cache)
    val fallbackState = if (entry.activeFallback) {
        val fallbackFrom = entry.fallbackFromProvider?.let { stringResource(it.displayNameRes) }
            ?: stringResource(R.string.settings_provider_health_primary)
        stringResource(R.string.settings_provider_health_fallback_active, fallbackFrom)
    } else {
        stringResource(R.string.settings_provider_health_fallback_inactive)
    }
    val description = stringResource(
        R.string.settings_provider_health_row_cd,
        typeLabel,
        providerLabel,
        successLabel,
        failureLabel,
        failureClass,
        cacheAge,
        fallbackState,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.42f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = description
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_provider_health_row_title, typeLabel, providerLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        ProviderHealthLine(stringResource(R.string.settings_provider_health_last_success, successLabel))
        ProviderHealthLine(stringResource(R.string.settings_provider_health_last_failure, failureLabel))
        ProviderHealthLine(stringResource(R.string.settings_provider_health_failure_class, failureClass))
        ProviderHealthLine(stringResource(R.string.settings_provider_health_cache_age, cacheAge))
        ProviderHealthLine(stringResource(R.string.settings_provider_health_fallback_state, fallbackState))
    }
}

@Composable
private fun ProviderHealthLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = NimbusTextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SettingsAdvancedSection(
    settings: NimbusSettings,
    transferStatus: SettingsTransferStatus?,
    transferInProgress: Boolean,
    pendingImportPreview: SettingsImportPreview?,
    actions: SettingsActions,
) {
    SettingSection(
        title = stringResource(R.string.settings_advanced_title),
        description = stringResource(R.string.settings_advanced_desc),
        initiallyExpanded = false,
    ) {
        Text(stringResource(R.string.settings_cache_duration), style = MaterialTheme.typography.bodySmall, color = NimbusTextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        listOf(15, 30, 60, 120).forEach { minutes ->
            SettingRadio(
                label = cacheDurationLabel(minutes),
                selected = settings.cacheTtlMinutes == minutes,
                onClick = { actions.onCacheTtlMinutes(minutes) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_transfer_title),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Text(
            stringResource(R.string.settings_transfer_desc),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsTransferButton(
                label = stringResource(R.string.settings_transfer_export),
                icon = Icons.Filled.FileUpload,
                enabled = !transferInProgress,
                modifier = Modifier.weight(1f),
                onClick = actions.onExportSettings,
            )
            SettingsTransferButton(
                label = stringResource(R.string.settings_transfer_import),
                icon = Icons.Filled.FileDownload,
                enabled = !transferInProgress,
                modifier = Modifier.weight(1f),
                onClick = actions.onImportSettings,
            )
        }
        if (transferInProgress) {
            SettingsTransferProgressCard()
        }
        pendingImportPreview?.let { preview ->
            SettingsImportPreviewCard(preview, actions, transferInProgress)
        }
        transferStatus?.let { status ->
            val statusMessage = settingsTransferStatusMessage(status)
            val isSuccess = status.tone == SettingsTransferStatusTone.SUCCESS
            Spacer(modifier = Modifier.height(10.dp))
            InlineNoticeCard(
                title = stringResource(R.string.settings_transfer_status_title),
                message = stringResource(R.string.settings_transfer_status_message, statusMessage),
                icon = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                tint = if (isSuccess) NimbusSuccess else NimbusError,
                liveRegion = LiveRegionMode.Polite,
                modifier = Modifier.clickable(
                    onClick = actions.onClearTransferStatus,
                    role = Role.Button,
                ),
            )
        }
    }
}

@Composable
private fun SettingsTransferProgressCard() {
    val workingTitle = stringResource(R.string.settings_transfer_working_title)
    val workingMessage = stringResource(R.string.settings_transfer_working_message)
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusBlueAccent.copy(alpha = 0.12f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "$workingTitle. $workingMessage"
                this.liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusBlueAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = NimbusBlueAccent,
                strokeWidth = 2.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workingTitle,
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = workingMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }
    }
}

@Composable
private fun settingsTransferStatusMessage(status: SettingsTransferStatus): String = when (status) {
    SettingsTransferStatus.ExportSuccess,
    SettingsTransferStatus.ExportError,
    SettingsTransferStatus.DiagnosticsExportSuccess,
    SettingsTransferStatus.DiagnosticsExportError,
    SettingsTransferStatus.ImportError -> stringResource(status.messageRes)
    is SettingsTransferStatus.ImportSuccess -> stringResource(
        status.messageRes,
        status.savedLocationCount,
        status.customAlertCount,
    )
}

@Composable
private fun SettingsImportPreviewCard(
    preview: SettingsImportPreview,
    actions: SettingsActions,
    transferInProgress: Boolean,
) {
    Spacer(modifier = Modifier.height(10.dp))
    InlineNoticeCard(
        title = stringResource(R.string.settings_transfer_preview_title),
        message = settingsImportPreviewMessage(preview),
        icon = Icons.Filled.FileDownload,
    )
    if (preview.warnings.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusCardBg.copy(alpha = 0.72f))
                .border(1.dp, NimbusCardBorder.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.settings_transfer_preview_warnings),
                style = MaterialTheme.typography.labelMedium,
                color = NimbusTextSecondary,
            )
            preview.warnings.forEach { warning ->
                Text(
                    text = "- ${settingsImportWarningText(warning, preview)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextTertiary,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsTransferButton(
            label = stringResource(R.string.settings_transfer_preview_cancel),
            icon = Icons.Filled.Close,
            modifier = Modifier.weight(1f),
            enabled = !transferInProgress,
            onClick = actions.onCancelSettingsImport,
        )
        SettingsTransferButton(
            label = stringResource(R.string.settings_transfer_preview_confirm),
            icon = Icons.Filled.FileDownload,
            modifier = Modifier.weight(1f),
            enabled = !transferInProgress,
            onClick = actions.onConfirmSettingsImport,
        )
    }
}

@Composable
private fun settingsImportPreviewMessage(preview: SettingsImportPreview): String {
    val lastLocation = if (preview.hasLastLocation) {
        stringResource(R.string.common_yes)
    } else {
        stringResource(R.string.common_no)
    }
    return stringResource(
        R.string.settings_transfer_preview_message,
        preview.schemaVersion,
        preview.currentSavedLocationCount,
        preview.savedLocationCount,
        preview.customAlertCount,
        preview.cardCount,
        lastLocation,
    )
}

@Composable
private fun settingsImportWarningText(
    warning: SettingsImportWarning,
    preview: SettingsImportPreview,
): String = when (warning) {
    SettingsImportWarning.BLANK_LOCATION_NAMES -> stringResource(
        R.string.settings_transfer_warning_blank_locations,
        preview.skippedLocationCount,
    )
    SettingsImportWarning.INVALID_COORDINATES -> stringResource(
        R.string.settings_transfer_warning_invalid_locations,
        preview.invalidLocationCount,
    )
    SettingsImportWarning.DUPLICATE_LOCATIONS -> stringResource(
        R.string.settings_transfer_warning_duplicate_locations,
        preview.duplicateLocationCount,
    )
    SettingsImportWarning.UNAVAILABLE_SOURCES -> stringResource(
        R.string.settings_transfer_warning_unavailable_sources,
    )
}

@Composable
private fun SettingsTransferButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NimbusBlueAccent,
            contentColor = NimbusTextPrimary,
            disabledContainerColor = NimbusCardBorder.copy(alpha = 0.64f),
            disabledContentColor = NimbusTextTertiary,
        ),
        onClick = onClick,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun cacheDurationLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.settings_minutes, minutes)
    minutes == 60 -> stringResource(R.string.settings_hour, 1)
    else -> stringResource(R.string.settings_hours, minutes / 60)
}

@Composable
private fun providerHealthTypeLabel(type: WeatherDataType): String = stringResource(
    when (type) {
        WeatherDataType.FORECAST -> R.string.settings_provider_health_type_forecast
        WeatherDataType.ALERTS -> R.string.settings_provider_health_type_alerts
        WeatherDataType.AIR_QUALITY -> R.string.settings_provider_health_type_air_quality
        WeatherDataType.MINUTELY -> R.string.settings_provider_health_type_minutely
    },
)

@Composable
private fun providerHealthRelativeTime(
    epochMs: Long?,
    nowEpochMs: Long,
    @StringRes emptyRes: Int,
): String {
    epochMs ?: return stringResource(emptyRes)
    val minutes = ((nowEpochMs - epochMs).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.settings_provider_health_just_now)
        minutes < 60 -> stringResource(R.string.settings_provider_health_minutes_ago, minutes)
        minutes < 1_440 -> stringResource(R.string.settings_provider_health_hours_ago, minutes / 60)
        else -> stringResource(R.string.settings_provider_health_days_ago, minutes / 1_440)
    }
}

private val ProviderFailureReason.labelRes: Int
    get() = when (this) {
        ProviderFailureReason.TIMEOUT -> R.string.settings_provider_health_reason_timeout
        ProviderFailureReason.HTTP_429 -> R.string.settings_provider_health_reason_http_429
        ProviderFailureReason.HTTP_5XX -> R.string.settings_provider_health_reason_http_5xx
        ProviderFailureReason.NETWORK -> R.string.settings_provider_health_reason_network
        ProviderFailureReason.UNSUPPORTED -> R.string.settings_provider_health_reason_unsupported
        ProviderFailureReason.UNKNOWN -> R.string.settings_provider_health_reason_unknown
    }

@Composable
private fun SettingsAboutSection() {
    SettingSection(
        title = stringResource(R.string.settings_about_title),
        description = stringResource(R.string.settings_about_desc),
    ) {
        SettingInfo(stringResource(R.string.settings_version), com.sysadmindoc.nimbus.BuildConfig.VERSION_NAME)
        SettingInfo(stringResource(R.string.settings_data_sources_title), stringResource(R.string.settings_data_sources_value))
        SettingInfo(stringResource(R.string.settings_privacy_label), stringResource(R.string.settings_privacy_value))
        SettingInfo(stringResource(R.string.settings_license), "LGPL-3.0")
    }
}

@Composable
private fun SettingsWidgetTroubleshootSection() {
    SettingSection(
        title = stringResource(R.string.settings_widget_troubleshoot_title),
        description = stringResource(R.string.settings_widget_troubleshoot_desc),
        initiallyExpanded = false,
    ) {
        SettingInfo(
            stringResource(R.string.settings_widget_troubleshoot_samsung_label),
            stringResource(R.string.settings_widget_troubleshoot_samsung_value),
        )
        SettingInfo(
            stringResource(R.string.settings_widget_troubleshoot_xiaomi_label),
            stringResource(R.string.settings_widget_troubleshoot_xiaomi_value),
        )
        SettingInfo(
            stringResource(R.string.settings_widget_troubleshoot_huawei_label),
            stringResource(R.string.settings_widget_troubleshoot_huawei_value),
        )
        SettingInfo(
            stringResource(R.string.settings_widget_troubleshoot_oneplus_label),
            stringResource(R.string.settings_widget_troubleshoot_oneplus_value),
        )
        SettingInfo(
            stringResource(R.string.settings_widget_troubleshoot_oppo_label),
            stringResource(R.string.settings_widget_troubleshoot_oppo_value),
        )
        SettingInfo(
            stringResource(R.string.settings_widget_troubleshoot_pixel_label),
            stringResource(R.string.settings_widget_troubleshoot_pixel_value),
        )
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
    val selectedLabel = stringResource(R.string.common_selected)
    val notSelectedLabel = stringResource(R.string.common_not_selected)
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
                    .width(148.dp)
                    .heightIn(min = 80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = if (isSelected) {
                                listOf(
                                    NimbusBlueAccent.copy(alpha = 0.18f),
                                    NimbusGlassTop.copy(alpha = 0.70f),
                                    NimbusGlassBottom,
                                )
                            } else {
                                listOf(
                                    NimbusGlassTop.copy(alpha = 0.48f),
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
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$categoryLabel, $categorySummary"
                        stateDescription = if (isSelected) selectedLabel else notSelectedLabel
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 7.dp)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
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
            .animateContentSize(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
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
                .heightIn(min = 52.dp)
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
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (selected) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = 0.14f),
                            NimbusGlassTop.copy(alpha = 0.62f),
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.42f),
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
            .padding(horizontal = 10.dp, vertical = 9.dp),
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
                .size(32.dp)
                .clearAndSetSemantics {},
        )
        Column(modifier = Modifier.padding(start = 6.dp).weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (checked) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = 0.12f),
                            NimbusGlassTop.copy(alpha = 0.58f),
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.40f),
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
private fun SettingValueButton(
    label: String,
    sublabel: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.42f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $sublabel, $value"
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = NimbusBlueAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
    val selectedLabel = stringResource(selected.displayNameRes)
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
                    val providerLabel = stringResource(provider.displayNameRes)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = providerLabel,
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
    val selectedLabel = selected?.let { stringResource(it.displayNameRes) } ?: noneLabel
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
                    val providerLabel = stringResource(provider.displayNameRes)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = providerLabel,
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
    isSecret: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Password,
    placeholder: String? = null,
) {
    var text by remember(label) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val fieldDescription = stringResource(R.string.settings_api_key_field_cd, label)
    // Latest value/callback for the onDispose commit below — the disposal
    // lambda is captured once, so direct captures would go stale.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val effectivePlaceholder = placeholder ?: stringResource(R.string.settings_enter_api_key)

    LaunchedEffect(value, isFocused) {
        if (!isFocused && value != text) {
            text = value
        }
    }

    fun commitValue() {
        val committed = text.trim()
        if (committed != currentValue) {
            currentOnValueChange(committed)
        }
        if (committed != text) {
            text = committed
        }
    }

    // Commit any pending text when the field leaves composition (back nav,
    // section collapse, category switch) so a typed key isn't silently lost.
    DisposableEffect(Unit) {
        onDispose { commitValue() }
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
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
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
            }.semantics {
                contentDescription = fieldDescription
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
                            text = effectivePlaceholder,
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

private fun formatBriefingTime(minutesAfterMidnight: Int, timeFormat: TimeFormat): String {
    val safeMinutes = minutesAfterMidnight.coerceIn(0, 24 * 60 - 1)
    val hour = safeMinutes / 60
    val minute = safeMinutes % 60
    return if (timeFormat == TimeFormat.TWENTY_FOUR_HOUR) {
        String.format(Locale.US, "%02d:%02d", hour, minute)
    } else {
        val displayHour = when (val twelveHour = hour % 12) {
            0 -> 12
            else -> twelveHour
        }
        val suffix = if (hour < 12) "AM" else "PM"
        String.format(Locale.US, "%d:%02d %s", displayHour, minute, suffix)
    }
}

internal fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
