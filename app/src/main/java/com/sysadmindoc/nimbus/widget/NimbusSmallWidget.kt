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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sysadmindoc.nimbus.MainActivity

/**
 * Small home screen widget: current temp, weather icon, location name.
 * Target size: 2x1 cells (~180x60dp)
 */
class NimbusSmallWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                SmallWidgetContent(data, strings)
            }
        }
    }
}

@Composable
private fun SmallWidgetContent(
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (data == null) {
            WidgetEmptyState(
                title = strings.emptyTitle,
                message = strings.smallEmptyMessage,
            )
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = strings.currentEyebrow,
                        style = widgetEyebrowStyle(),
                    )
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = data.locationName,
                        style = WidgetTheme.titleStyle,
                        maxLines = 1,
                    )
                }
                strings.updatedLabel(data.updatedAt)?.let { label ->
                    WidgetStatusBadge(
                        text = label,
                        onClick = widgetRefreshBadgeAction(),
                        contentDescription = strings.updatedContentDescription(label),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(14.dp)
                        .background(WidgetTheme.cardColor)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                        contentDescription = strings.weatherDescription(data.weatherCode, data.isDay, data.conditionText),
                        modifier = GlanceModifier.size(30.dp),
                    )
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${data.temperature.toInt()}\u00B0",
                        style = TextStyle(
                            color = WidgetTheme.textPrimary,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = strings.feelsHumidity(data.feelsLike.toInt(), data.humidity),
                        style = WidgetTheme.captionStyle,
                        maxLines = 1,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = strings.highTemp(data.high.toInt()),
                        style = WidgetTheme.labelStyle,
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = strings.lowTemp(data.low.toInt()),
                        style = WidgetTheme.captionStyle,
                    )
                }
            }
        }
    }
}

class NimbusSmallWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusSmallWidget()
}
