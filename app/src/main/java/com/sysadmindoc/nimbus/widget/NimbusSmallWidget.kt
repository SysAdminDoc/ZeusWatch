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
        val data = WidgetDataProvider.load(context)
        provideContent {
            GlanceTheme {
                SmallWidgetContent(data)
            }
        }
    }
}

@Composable
private fun SmallWidgetContent(data: WidgetWeatherData?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(WidgetTheme.bgColor)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data == null) {
            Text(
                text = "Tap to load",
                style = WidgetTheme.labelStyle,
            )
        } else {
            // Weather icon
            Image(
                provider = ImageProvider(weatherIconRes(data.weatherCode, data.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
            )

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Temp + location
            Column {
                Text(
                    text = "${data.temperature.toInt()}\u00B0",
                    style = TextStyle(
                        color = WidgetTheme.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                )
                Text(
                    text = data.locationName,
                    style = WidgetTheme.locationStyle,
                    maxLines = 1,
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // High / Low
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "H:${data.high.toInt()}\u00B0",
                    style = WidgetTheme.labelStyle,
                )
                Text(
                    text = "L:${data.low.toInt()}\u00B0",
                    style = TextStyle(
                        color = WidgetTheme.textTertiary,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

class NimbusSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NimbusSmallWidget()
}
