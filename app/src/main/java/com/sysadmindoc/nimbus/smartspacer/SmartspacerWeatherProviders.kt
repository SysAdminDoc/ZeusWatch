package com.sysadmindoc.nimbus.smartspacer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kieronquinn.app.smartspacer.sdk.model.CompatibilityState
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.receivers.SmartspacerComplicationUpdateReceiver
import com.kieronquinn.app.smartspacer.sdk.receivers.SmartspacerTargetUpdateReceiver
import com.kieronquinn.app.smartspacer.sdk.utils.ComplicationTemplate
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import com.sysadmindoc.nimbus.MainActivity
import com.sysadmindoc.nimbus.R
import kotlinx.coroutines.runBlocking
import android.graphics.drawable.Icon as AndroidIcon

private const val REFRESH_PERIOD_MINUTES = 30
private const val TARGET_ID = "zeuswatch_weather"
private const val COMPLICATION_ID = "zeuswatch_temperature"

class ZeusWatchWeatherTargetProvider : SmartspacerTargetProvider() {
    private val cache by lazy { SmartspacerWeatherCache(provideContext().applicationContext) }

    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val snapshot = runBlocking { cache.loadSnapshot() } ?: return emptyList()
        return listOf(
            TargetTemplate.Basic(
                id = TARGET_ID,
                componentName = ComponentName(provideContext(), ZeusWatchWeatherTargetProvider::class.java),
                featureType = SmartspaceTarget.FEATURE_WEATHER,
                title = Text(snapshot.targetTitle),
                subtitle = Text(snapshot.targetSubtitle),
                icon = snapshot.smartspacerIcon(provideContext()),
                onClick = TapAction(intent = provideContext().launchWeatherIntent()),
            ).create().apply {
                canBeDismissed = false
            },
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean = false

    override fun getConfig(smartspacerId: String?): Config {
        val context = provideContext()
        return Config(
            label = context.getString(R.string.smartspacer_weather_target_label),
            description = context.getString(R.string.smartspacer_weather_target_description),
            icon = AndroidIcon.createWithResource(context, R.drawable.ic_w_partly_cloudy),
            refreshPeriodMinutes = REFRESH_PERIOD_MINUTES,
            refreshIfNotVisible = true,
            compatibilityState = smartspacerCompatibilityState(context),
        )
    }
}

class ZeusWatchWeatherComplicationProvider : SmartspacerComplicationProvider() {
    private val cache by lazy { SmartspacerWeatherCache(provideContext().applicationContext) }

    override fun getSmartspaceActions(smartspacerId: String): List<SmartspaceAction> {
        val snapshot = runBlocking { cache.loadSnapshot() } ?: return emptyList()
        return listOf(
            ComplicationTemplate.Basic(
                id = COMPLICATION_ID,
                icon = snapshot.smartspacerIcon(provideContext()),
                content = Text(snapshot.complicationText),
                onClick = TapAction(intent = provideContext().launchWeatherIntent()),
            ).create(),
        )
    }

    override fun getConfig(smartspacerId: String?): Config {
        val context = provideContext()
        return Config(
            label = context.getString(R.string.smartspacer_weather_complication_label),
            description = context.getString(R.string.smartspacer_weather_complication_description),
            icon = AndroidIcon.createWithResource(context, R.drawable.ic_w_sunny),
            refreshPeriodMinutes = REFRESH_PERIOD_MINUTES,
            refreshIfNotVisible = true,
            compatibilityState = smartspacerCompatibilityState(context),
        )
    }
}

class ZeusWatchSmartspacerTargetUpdateReceiver : SmartspacerTargetUpdateReceiver() {
    override fun onRequestSmartspaceTargetUpdate(
        context: Context,
        requestTargets: List<RequestTarget>,
    ) {
        requestTargets.forEach { request ->
            SmartspacerTargetProvider.notifyChange(context, request.authority, request.smartspacerId)
        }
    }
}

class ZeusWatchSmartspacerComplicationUpdateReceiver : SmartspacerComplicationUpdateReceiver() {
    override fun onRequestSmartspaceComplicationUpdate(
        context: Context,
        requestComplications: List<RequestComplication>,
    ) {
        requestComplications.forEach { request ->
            SmartspacerComplicationProvider.notifyChange(context, request.authority, request.smartspacerId)
        }
    }
}

private fun SmartspacerWeatherSnapshot.smartspacerIcon(context: Context): Icon =
    Icon(
        icon = AndroidIcon.createWithResource(context, iconRes),
        contentDescription = condition,
        shouldTint = false,
    )

private fun Context.launchWeatherIntent(): Intent =
    Intent(this, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("zeuswatch://weather")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private fun smartspacerCompatibilityState(context: Context): CompatibilityState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        CompatibilityState.Compatible
    } else {
        CompatibilityState.Incompatible(context.getString(R.string.smartspacer_weather_requires_android_q))
    }
