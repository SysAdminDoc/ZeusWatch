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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.sysadmindoc.nimbus.MainActivity

class NimbusDailyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetDataProvider.load(context, appWidgetId)
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                DailyWidgetContent(data, strings)
            }
        }
    }
}

@Composable
private fun DailyWidgetContent(
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
                message = strings.tapToOpen,
            )
            return@Column
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.fiveDayOutlook,
                style = widgetEyebrowStyle(),
                modifier = GlanceModifier.defaultWeight(),
            )
            strings.updatedLabel(data.updatedAt)?.let { label ->
                WidgetStatusBadge(
                    text = label,
                    onClick = widgetRefreshBadgeAction(),
                    contentDescription = strings.updatedContentDescription(label),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(WidgetTheme.cardColor)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            data.daily.take(5).forEach { day ->
                DailyRow(day, strings)
            }
        }
    }
}

@Composable
private fun DailyRow(
    day: WidgetDaily,
    strings: WidgetStrings,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day.day,
            style = WidgetTheme.locationStyle,
            modifier = GlanceModifier.width(48.dp),
        )

        Image(
            provider = ImageProvider(weatherIconRes(day.code, true)),
            contentDescription = strings.weatherDescription(day.code, true, day.conditionText),
            modifier = GlanceModifier.size(16.dp),
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        if (day.precipChance > 0) {
            Text(
                "${day.precipChance}%",
                style = widgetPrecipStyle(),
                modifier = GlanceModifier.width(28.dp),
            )
        } else {
            Spacer(modifier = GlanceModifier.width(28.dp))
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Text(
            "${day.high}°",
            style = WidgetTheme.tempSmall,
            modifier = GlanceModifier.width(30.dp),
        )
        Text(
            "${day.low}°",
            style = WidgetTheme.captionStyle,
            modifier = GlanceModifier.width(30.dp),
        )
    }
}

class NimbusDailyWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusDailyWidget()
}
