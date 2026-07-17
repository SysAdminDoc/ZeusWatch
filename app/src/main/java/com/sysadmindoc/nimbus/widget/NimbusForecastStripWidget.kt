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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import kotlin.math.roundToInt

/**
 * Compact 4x1 home screen widget showing current temp + next 5 hourly temps.
 * Designed for a single-row horizontal strip.
 */
class NimbusForecastStripWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                ForecastStripContent(data, strings)
            }
        }
    }
}

@Composable
private fun ForecastStripContent(
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
            WidgetEmptyState(
                title = strings.emptyTitle,
                message = strings.stripEmptyMessage,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.width(64.dp),
            ) {
                Text(
                    strings.nextEyebrow,
                    style = widgetEyebrowStyle(),
                    maxLines = 1,
                )
                Text(data.locationName, style = WidgetTheme.captionStyle, maxLines = 1)
                Spacer(modifier = GlanceModifier.height(3.dp))
                Image(
                    provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                    contentDescription = strings.weatherDescription(data.weatherCode, data.isDay, data.conditionText),
                    modifier = GlanceModifier.size(18.dp),
                )
                Text(
                    "${data.temperature.roundToInt()}\u00B0",
                    style = WidgetTheme.tempSmall,
                )
                strings.updatedLabel(data.updatedAt)?.let { label ->
                    WidgetMiniStatusBadge(
                        text = label,
                        onClick = widgetRefreshBadgeAction(),
                        contentDescription = strings.updatedContentDescription(label),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Glance hard-caps a Row/Column at 10 direct children and silently
            // truncates the rest (logged as "Row container cannot have more
            // than 10 elements"). The leading Column + spacer already take 2
            // outer slots; 5 hourly columns interleaved with spacers would push
            // the outer Row to 12 children and drop the last hours. Nest the
            // hourly cells in their own Row so the outer Row stays at 3 children
            // and the inner Row holds at most 5 columns + 4 spacers = 9.
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val upcoming = data.hourly.drop(1).take(5)
                upcoming.forEachIndexed { index, hour ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(4.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .cornerRadius(10.dp)
                            .background(WidgetTheme.cardColor)
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                    ) {
                        Text(
                            hour.hour,
                            style = WidgetTheme.captionStyle,
                        )
                        Image(
                            provider = ImageProvider(weatherIconRes(hour.code, hour.isDay)),
                            contentDescription = strings.weatherDescription(hour.code, hour.isDay, hour.conditionText),
                            modifier = GlanceModifier.size(14.dp),
                        )
                        Text(
                            "${hour.temp}\u00B0",
                            style = WidgetTheme.tempSmall,
                        )
                        if (hour.precipChance > 20) {
                            Text(
                                "${hour.precipChance}%",
                                style = widgetPrecipStyle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

class NimbusForecastStripWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusForecastStripWidget()
}
