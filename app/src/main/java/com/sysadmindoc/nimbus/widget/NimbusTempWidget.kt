package com.sysadmindoc.nimbus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sysadmindoc.nimbus.MainActivity

class NimbusTempWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                TempWidgetContent(data, strings)
            }
        }
    }
}

@Composable
private fun TempWidgetContent(
    data: WidgetWeatherData?,
    strings: WidgetStrings,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(12.dp)
            .background(WidgetTheme.bgColor)
            .clickable(
                if (data != null) actionStartActivity<MainActivity>()
                else actionRunCallback<WidgetRefreshAction>()
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data == null) {
            Text(
                text = "—",
                style = TextStyle(
                    color = WidgetTheme.textSecondary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        } else {
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = strings.weatherDescription(data.weatherCode, data.isDay),
                modifier = GlanceModifier.size(28.dp),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "${data.temperature.toInt()}°",
                style = TextStyle(
                    color = WidgetTheme.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            strings.updatedLabel(data.updatedAt)?.let { label ->
                WidgetMiniStatusBadge(
                    text = label,
                    onClick = widgetRefreshBadgeAction(),
                    contentDescription = strings.updatedContentDescription(label),
                )
            }
        }
    }
}

class NimbusTempWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusTempWidget()
}
