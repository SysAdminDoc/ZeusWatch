package com.sysadmindoc.nimbus.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity as actionStartActivityClass
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sysadmindoc.nimbus.MainActivity

/**
 * 4x3/4x4 widget listing several saved cities from the widget weather cache.
 */
class NimbusSavedCitiesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cities = WidgetDataProvider.loadSavedCities(context)
        val strings = widgetStrings(context)
        provideContent {
            GlanceTheme {
                SavedCitiesWidgetContent(cities, strings)
            }
        }
    }
}

@Composable
private fun SavedCitiesWidgetContent(
    cities: List<WidgetSavedCity>,
    strings: WidgetStrings,
) {
    val containerModifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(12.dp)
        .background(WidgetTheme.bgColor)
    val interactiveModifier = if (cities.isEmpty()) {
        containerModifier.clickable(actionStartActivityClass<MainActivity>())
    } else {
        containerModifier
    }
    Column(
        modifier = interactiveModifier.padding(14.dp),
    ) {
        HeaderRow(cities, strings)
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (cities.isEmpty()) {
            WidgetEmptyState(
                title = strings.emptyTitle,
                message = strings.savedCitiesEmptyMessage,
            )
            return@Column
        }

        cities.take(5).forEachIndexed { index, city ->
            if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
            SavedCityRow(city, strings)
        }
    }
}

@Composable
private fun HeaderRow(
    cities: List<WidgetSavedCity>,
    strings: WidgetStrings,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(strings.savedCitiesEyebrow, style = widgetEyebrowStyle())
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(strings.tapToOpen, style = WidgetTheme.captionStyle)
        }
        val newestUpdate = cities.maxOfOrNull { it.updatedAt } ?: 0L
        strings.updatedLabel(newestUpdate)?.let { label ->
            WidgetStatusBadge(
                text = label,
                onClick = actionRunCallback<WidgetRefreshAction>(),
                contentDescription = strings.updatedContentDescription(label),
            )
        }
    }
}

@Composable
private fun SavedCityRow(
    city: WidgetSavedCity,
    strings: WidgetStrings,
) {
    val tempLabel = city.temperature?.let { "$it\u00B0" } ?: strings.savedCityUnavailableTemp
    val rowDescription = strings.savedCityContentDescription(city.locationName, tempLabel)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(10.dp)
            .background(WidgetTheme.cardColor)
            .clickable(actionStartActivityIntent(savedCityIntent(city.locationId)))
            .semantics { contentDescription = rowDescription }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        city.weatherCode?.let { code ->
            Image(
                provider = ImageProvider(weatherIconRes(code, city.isDay)),
                contentDescription = strings.weatherDescription(code, city.isDay),
                modifier = GlanceModifier.size(22.dp),
            )
        } ?: Spacer(modifier = GlanceModifier.size(22.dp))
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(city.locationName, style = WidgetTheme.locationStyle, maxLines = 1)
            SavedCityMeta(city, strings)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                tempLabel,
                style = TextStyle(
                    color = WidgetTheme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            SavedCityHighLow(city, strings)
        }
    }
}

@Composable
private fun SavedCityMeta(
    city: WidgetSavedCity,
    strings: WidgetStrings,
) {
    val updated = strings.updatedLabel(city.updatedAt)
    Text(
        text = updated ?: strings.savedCityUnavailableTemp,
        style = WidgetTheme.captionStyle,
        maxLines = 1,
    )
}

@Composable
private fun SavedCityHighLow(
    city: WidgetSavedCity,
    strings: WidgetStrings,
) {
    val high = city.high
    val low = city.low
    if (high == null || low == null) {
        Text(strings.savedCityUnavailableTemp, style = WidgetTheme.captionStyle, maxLines = 1)
    } else {
        Text(
            "${strings.highTemp(high)}  ${strings.lowTemp(low)}",
            style = WidgetTheme.captionStyle,
            maxLines = 1,
        )
    }
}

private fun savedCityIntent(locationId: Long): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse("zeuswatch://main?locationId=$locationId"))
}

class NimbusSavedCitiesWidgetReceiver : NimbusWidgetReceiverBase() {
    override val glanceAppWidget = NimbusSavedCitiesWidget()
}
