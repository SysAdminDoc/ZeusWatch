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
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                LargeWidgetContent(data, strings)
            }
        }
    }
}

@Composable
private fun LargeWidgetContent(
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
            .padding(14.dp),
    ) {
        if (data == null) {
            WidgetEmptyState(
                title = strings.emptyTitle,
                message = strings.largeEmptyMessage,
            )
            return@Column
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    strings.detailedOutlookEyebrow,
                    style = WidgetTheme.eyebrowStyle,
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    data.locationName,
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

        Spacer(modifier = GlanceModifier.height(10.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = strings.weatherDescription(data.weatherCode, data.isDay),
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
                    text = strings.feelsHumidity(data.feelsLike.toInt(), data.humidity),
                    style = WidgetTheme.labelStyle,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(strings.highTemp(data.high.toInt()), style = WidgetTheme.labelStyle)
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(strings.lowTemp(data.low.toInt()), style = WidgetTheme.captionStyle)
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Text(strings.next6Hours, style = WidgetTheme.eyebrowStyle)
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
                HourColumn(hour, strings, modifier = GlanceModifier.defaultWeight())
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        Text(strings.fiveDayOutlook, style = WidgetTheme.eyebrowStyle)
        Spacer(modifier = GlanceModifier.height(6.dp))

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(WidgetTheme.cardColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            data.daily.take(5).forEach { day ->
                DayRow(day, strings)
            }
        }
    }
}

@Composable
private fun HourColumn(
    hour: WidgetHourly,
    strings: WidgetStrings,
    modifier: GlanceModifier = GlanceModifier,
) {
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
            contentDescription = strings.weatherDescription(hour.code, hour.isDay),
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
private fun DayRow(
    day: WidgetDaily,
    strings: WidgetStrings,
) {
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
            contentDescription = strings.weatherDescription(day.code, true),
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
