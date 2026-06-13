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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.sysadmindoc.nimbus.util.displayNameRes
import com.sysadmindoc.nimbus.util.labelRes
import com.sysadmindoc.nimbus.util.summaryRes

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
    val transferStatus by viewModel.transferStatus.collectAsStateWithLifecycle()
    val pendingImportPreview by viewModel.pendingImportPreview.collectAsStateWithLifecycle()
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportSettings(uri)
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importSettings(uri)
    }
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onNavigateToCustomAlerts = onNavigateToCustomAlerts,
        notificationsPermissionGranted = notificationsPermissionGranted,
        availableIconPacks = availableIconPacks,
        transferStatus = transferStatus,
        pendingImportPreview = pendingImportPreview,
        actions = SettingsActions(
            onTempUnit = viewModel::setTempUnit,
            onWindUnit = viewModel::setWindUnit,
            onPressureUnit = viewModel::setPressureUnit,
            onPrecipUnit = viewModel::setPrecipUnit,
            onTimeFormat = viewModel::setTimeFormat,
            onVisibilityUnit = viewModel::setVisibilityUnit,
            onParticlesEnabled = viewModel::setParticlesEnabled,
            onAlertNotificationsEnabled = { enabled ->
                if (enabled) {
                    enableNotificationsIfPermitted { viewModel.setAlertNotificationsEnabled(true) }
                } else {
                    viewModel.setAlertNotificationsEnabled(false)
                }
            },
            onAlertMinSeverity = viewModel::setAlertMinSeverity,
            onAlertCheckAllLocations = viewModel::setAlertCheckAllLocations,
            onAlertSourcePref = viewModel::setAlertSourcePref,
            onRadarProvider = viewModel::setRadarProvider,
            onIconStyle = viewModel::setIconStyle,
            onCustomIconPackId = viewModel::setCustomIconPackId,
            onThemeMode = viewModel::setThemeMode,
            onSummaryStyle = viewModel::setSummaryStyle,
            onCardEnabled = viewModel::setCardEnabled,
            onMoveCard = viewModel::moveCard,
            onResetCardPreferences = viewModel::resetCardPreferences,
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
            onDailyBriefingEnabled = { enabled ->
                if (enabled) {
                    enableNotificationsIfPermitted { viewModel.setDailyBriefingEnabled(true) }
                } else {
                    viewModel.setDailyBriefingEnabled(false)
                }
            },
            onDailyBriefingMinutes = viewModel::setDailyBriefingMinutes,
            onDrivingAlerts = viewModel::setDrivingAlerts,
            onHealthAlertsEnabled = viewModel::setHealthAlertsEnabled,
            onShowSnowfall = viewModel::setShowSnowfall,
            onShowCape = viewModel::setShowCape,
            onShowSunshineDuration = viewModel::setShowSunshineDuration,
            onShowGoldenHour = viewModel::setShowGoldenHour,
            onShowBeaufortColors = viewModel::setShowBeaufortColors,
            onShowOutdoorScore = viewModel::setShowOutdoorScore,
            onShowYesterdayComparison = viewModel::setShowYesterdayComparison,
            onHourlyForecastHours = viewModel::setHourlyForecastHours,
            onMigraineAlerts = viewModel::setMigraineAlerts,
            onMigrainePressureThreshold = viewModel::setMigrainePressureThreshold,
            onHapticFeedbackForAlerts = viewModel::setHapticFeedbackForAlerts,
            onAccessibilityLayout = viewModel::setAccessibilityLayout,
            onCacheTtlMinutes = viewModel::setCacheTtlMinutes,
            onSourceForecast = viewModel::setSourceForecast,
            onSourceForecastFallback = viewModel::setSourceForecastFallback,
            onSourceAlerts = viewModel::setSourceAlerts,
            onSourceAlertsFallback = viewModel::setSourceAlertsFallback,
            onSourceAirQuality = viewModel::setSourceAirQuality,
            onSourceMinutely = viewModel::setSourceMinutely,
            onGadgetbridgeBroadcastEnabled = viewModel::setGadgetbridgeBroadcastEnabled,
            onOwmApiKey = viewModel::setOwmApiKey,
            onPirateWeatherApiKey = viewModel::setPirateWeatherApiKey,
            onExportSettings = { exportSettingsLauncher.launch("zeuswatch-settings.json") },
            onImportSettings = { importSettingsLauncher.launch(arrayOf("application/json", "text/*")) },
            onConfirmSettingsImport = viewModel::confirmPendingImport,
            onCancelSettingsImport = viewModel::cancelPendingImport,
            onClearTransferStatus = viewModel::clearTransferStatus,
        ),
    )
}
