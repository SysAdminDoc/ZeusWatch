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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sysadmindoc.nimbus.MainActivity

class NimbusCompactWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                CompactWidgetContent(data, strings)
            }
        }
    }
}

@Composable
private fun CompactWidgetContent(
    data: WidgetWeatherData?,
    strings: WidgetStrings,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(12.dp)
            .background(WidgetTheme.bgColor)
            .clickable(
                if (data != null) actionStartActivity<MainActivity>()
                else actionRunCallback<WidgetRefreshAction>()
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data == null) {
            Text(
                text = "—",
                style = TextStyle(
                    color = WidgetTheme.textSecondary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        } else {
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = strings.weatherDescription(data.weatherCode, data.isDay, data.conditionText),
                modifier = GlanceModifier.size(28.dp),
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${data.temperature.toInt()}°",
                    style = TextStyle(
                        color = WidgetTheme.textPrimary,
                        fontSize = 22.sp,
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

            Spacer(modifier = GlanceModifier.defaultWeight())

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = strings.highTemp(data.high.toInt()),
                    style = WidgetTheme.labelStyle,
                )
                Text(
                    text = strings.lowTemp(data.low.toInt()),
                    style = WidgetTheme.captionStyle,
                )
            }
        }
    }
}

class NimbusCompactWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusCompactWidget()
}
