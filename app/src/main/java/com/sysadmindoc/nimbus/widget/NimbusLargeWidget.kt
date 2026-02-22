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
 * Large home screen widget: current conditions + hourly strip + 5-day forecast.
 * Target size: 4x3 cells (~360x180dp)
 */
class NimbusLargeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataProvider.load(context)
        provideContent {
            GlanceTheme {
                LargeWidgetContent(data)
            }
        }
    }
}

@Composable
private fun LargeWidgetContent(data: WidgetWeatherData?) {
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

        // Row 1: Current conditions
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "${data.temperature.toInt()}\u00B0",
                style = TextStyle(
                    color = WidgetTheme.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column {
                Text(data.locationName, style = WidgetTheme.locationStyle, maxLines = 1)
                Text(
                    "H:${data.high.toInt()}\u00B0 L:${data.low.toInt()}\u00B0  Humidity ${data.humidity}%",
                    style = WidgetTheme.highLowStyle,
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Row 2: Hourly strip (6 hours)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(WidgetTheme.cardColor)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            data.hourly.take(6).forEachIndexed { idx, hour ->
                if (idx > 0) Spacer(modifier = GlanceModifier.width(2.dp))
                HourColumn(hour, modifier = GlanceModifier.defaultWeight())
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Row 3: 5-day forecast
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(WidgetTheme.cardColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            data.daily.take(5).forEach { day ->
                DayRow(day)
            }
        }
    }
}

@Composable
private fun HourColumn(hour: WidgetHourly, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            hour.hour,
            style = TextStyle(color = WidgetTheme.textTertiary, fontSize = 9.sp),
            maxLines = 1,
        )
        Image(
            provider = ImageProvider(weatherIconRes(hour.code, hour.isDay)),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp),
        )
        Text("${hour.temp}\u00B0", style = WidgetTheme.tempSmall)
        if (hour.precipChance > 20) {
            Text(
                "${hour.precipChance}%",
                style = WidgetTheme.precipStyle,
            )
        }
    }
}

@Composable
private fun DayRow(day: WidgetDaily) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Day name
        Text(
            day.day,
            style = TextStyle(
                color = WidgetTheme.textPrimary,
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.width(48.dp),
        )

        // Icon
        Image(
            provider = ImageProvider(weatherIconRes(day.code, true)),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp),
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        // Precip
        if (day.precipChance > 0) {
            Text(
                "${day.precipChance}%",
                style = WidgetTheme.precipStyle,
                modifier = GlanceModifier.width(28.dp),
            )
        } else {
            Spacer(modifier = GlanceModifier.width(28.dp))
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Temps
        Text(
            "${day.high}\u00B0",
            style = WidgetTheme.tempSmall,
            modifier = GlanceModifier.width(30.dp),
        )
        Text(
            "${day.low}\u00B0",
            style = TextStyle(color = WidgetTheme.textTertiary, fontSize = 12.sp),
            modifier = GlanceModifier.width(30.dp),
        )
    }
}

class NimbusLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NimbusLargeWidget()
}
