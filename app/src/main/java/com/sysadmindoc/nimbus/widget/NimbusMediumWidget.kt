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
        val data = WidgetDataProvider.load(context)
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
            .cornerRadius(16.dp)
            .background(WidgetTheme.bgColor)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        if (data == null) {
            Text("Tap to load weather", style = WidgetTheme.labelStyle)
            return@Column
        }

        // Top: current conditions row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "${data.temperature.toInt()}\u00B0",
                style = TextStyle(
                    color = WidgetTheme.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column {
                Text(data.locationName, style = WidgetTheme.locationStyle, maxLines = 1)
                Text(
                    "H:${data.high.toInt()}\u00B0 L:${data.low.toInt()}\u00B0",
                    style = WidgetTheme.highLowStyle,
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Bottom: 3-day forecast
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
            .cornerRadius(10.dp)
            .background(WidgetTheme.cardColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day.day, style = WidgetTheme.labelStyle)
        Spacer(modifier = GlanceModifier.height(2.dp))
        Image(
            provider = ImageProvider(weatherIconRes(day.code, true)),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text("${day.high}\u00B0", style = WidgetTheme.tempSmall)
        Text(
            "${day.low}\u00B0",
            style = TextStyle(color = WidgetTheme.textTertiary, fontSize = 11.sp),
        )
    }
}

class NimbusMediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NimbusMediumWidget()
}
