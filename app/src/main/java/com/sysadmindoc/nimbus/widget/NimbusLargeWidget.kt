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
 * Large home screen widget: current conditions + hourly strip + 5-day forecast.
 * Target size: 4x3 cells (~360x180dp)
 */
class NimbusLargeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
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
                message = "Tap to load current conditions, hourly changes, and the 5-day outlook.",
            )
            return@Column
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "DETAILED OUTLOOK",
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
                modifier = GlanceModifier.size(32.dp),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "${data.temperature.toInt()}\u00B0",
                    style = TextStyle(
                        color = WidgetTheme.textPrimary,
                        fontSize = 28.sp,
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
                Text("H ${data.high.toInt()}\u00B0", style = WidgetTheme.labelStyle)
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text("L ${data.low.toInt()}\u00B0", style = WidgetTheme.captionStyle)
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Text("NEXT 6 HOURS", style = WidgetTheme.eyebrowStyle)
        Spacer(modifier = GlanceModifier.height(6.dp))

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(WidgetTheme.cardColor)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            data.hourly.take(6).forEachIndexed { idx, hour ->
                if (idx > 0) Spacer(modifier = GlanceModifier.width(2.dp))
                HourColumn(hour, modifier = GlanceModifier.defaultWeight())
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Text("5-DAY OUTLOOK", style = WidgetTheme.eyebrowStyle)
        Spacer(modifier = GlanceModifier.height(6.dp))

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(WidgetTheme.cardColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
            style = WidgetTheme.captionStyle,
            maxLines = 1,
        )
        Image(
            provider = ImageProvider(weatherIconRes(hour.code, hour.isDay)),
            contentDescription = WidgetUtils.weatherDescription(hour.code, hour.isDay),
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
            style = WidgetTheme.locationStyle,
            modifier = GlanceModifier.width(48.dp),
        )

        // Icon (daily forecasts use daytime icons)
        Image(
            provider = ImageProvider(weatherIconRes(day.code, true)),
            contentDescription = WidgetUtils.weatherDescription(day.code, true),
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
            style = WidgetTheme.captionStyle,
            modifier = GlanceModifier.width(30.dp),
        )
    }
}

class NimbusLargeWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusLargeWidget()
}
