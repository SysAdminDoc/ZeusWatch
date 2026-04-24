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
 * Medium home screen widget: current temp + 3-day forecast row.
 * Target size: 3x2 cells (~270x120dp)
 */
class NimbusMediumWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        provideContent {
            GlanceTheme {
                MediumWidgetContent(data)
            }
        }
    }
}

@Composable
private fun MediumWidgetContent(data: WidgetWeatherData?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(18.dp)
            .background(WidgetTheme.bgColor)
            .clickable(
                if (data != null) actionStartActivity<MainActivity>()
                else actionRunCallback<WidgetRefreshAction>()
            )
            .padding(14.dp),
    ) {
        if (data == null) {
            WidgetEmptyState(
                title = "ZeusWatch",
                message = "Tap to load the latest forecast and next few days.",
            )
            return@Column
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "OVERVIEW",
                    style = WidgetTheme.eyebrowStyle,
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    data.locationName,
                    style = WidgetTheme.titleStyle,
                    maxLines = 1,
                )
            }
            widgetUpdatedLabel(data.updatedAt)?.let { label ->
                WidgetPill(
                    text = label,
                    onClick = widgetRefreshPillAction(),
                    contentDescription = "Data updated $label ago. Tap to refresh now.",
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = WidgetUtils.weatherDescription(data.weatherCode, data.isDay),
                modifier = GlanceModifier.size(30.dp),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "${data.temperature.toInt()}\u00B0",
                    style = TextStyle(
                        color = WidgetTheme.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = "Feels ${data.feelsLike.toInt()}\u00B0 \u2022 Humidity ${data.humidity}%",
                    style = WidgetTheme.labelStyle,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "H ${data.high.toInt()}\u00B0",
                    style = WidgetTheme.labelStyle,
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "L ${data.low.toInt()}\u00B0",
                    style = WidgetTheme.captionStyle,
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NEXT 3 DAYS", style = WidgetTheme.eyebrowStyle)
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text("Tap to open", style = WidgetTheme.captionStyle)
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            data.daily.take(3).forEachIndexed { idx, day ->
                if (idx > 0) Spacer(modifier = GlanceModifier.width(4.dp))
                DayColumn(day, modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun DayColumn(day: WidgetDaily, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .cornerRadius(12.dp)
            .background(WidgetTheme.cardColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day.day, style = WidgetTheme.captionStyle)
        Spacer(modifier = GlanceModifier.height(3.dp))
        Image(
            provider = ImageProvider(weatherIconRes(day.code, true)),
            contentDescription = WidgetUtils.weatherDescription(day.code, true),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text("${day.high}\u00B0", style = WidgetTheme.tempSmall)
        Text(
            "${day.low}\u00B0",
            style = WidgetTheme.captionStyle,
        )
    }
}

class NimbusMediumWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusMediumWidget()
}
