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

/**
 * Compact 4x1 home screen widget showing current temp + next 5 hourly temps.
 * Designed for a single-row horizontal strip.
 */
class NimbusForecastStripWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        provideContent {
            GlanceTheme {
                ForecastStripContent(data)
            }
        }
    }
}

@Composable
private fun ForecastStripContent(data: WidgetWeatherData?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(WidgetTheme.bgColor)
            .clickable(
                if (data != null) actionStartActivity<MainActivity>()
                else actionRunCallback<WidgetRefreshAction>()
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data == null) {
            Text(
                "ZeusWatch \u2022 Tap to load",
                style = WidgetTheme.labelStyle,
            )
        } else {
            // Current temp + icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.width(52.dp),
            ) {
                Text(
                    "Now",
                    style = TextStyle(
                        color = WidgetTheme.textSecondary,
                        fontSize = 9.sp,
                    ),
                )
                Image(
                    provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                    contentDescription = WidgetUtils.weatherDescription(data.weatherCode, data.isDay),
                    modifier = GlanceModifier.size(20.dp),
                )
                Text(
                    "${data.temperature.toInt()}\u00B0",
                    style = TextStyle(
                        color = WidgetTheme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            // Divider space
            Spacer(modifier = GlanceModifier.width(4.dp))

            // Next 5 hours (guard: skip if insufficient data)
            val upcoming = data.hourly.drop(1).take(5)
            upcoming.forEach { hour ->
                Spacer(modifier = GlanceModifier.width(2.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.defaultWeight(),
                ) {
                    Text(
                        hour.hour,
                        style = TextStyle(
                            color = WidgetTheme.textTertiary,
                            fontSize = 9.sp,
                        ),
                    )
                    Image(
                        provider = ImageProvider(weatherIconRes(hour.code, hour.isDay)),
                        contentDescription = WidgetUtils.weatherDescription(hour.code, hour.isDay),
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Text(
                        "${hour.temp}\u00B0",
                        style = TextStyle(
                            color = WidgetTheme.textPrimary,
                            fontSize = 12.sp,
                        ),
                    )
                    if (hour.precipChance > 20) {
                        Text(
                            "${hour.precipChance}%",
                            style = TextStyle(
                                color = WidgetTheme.textSecondary,
                                fontSize = 8.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

class NimbusForecastStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NimbusForecastStripWidget()
}
