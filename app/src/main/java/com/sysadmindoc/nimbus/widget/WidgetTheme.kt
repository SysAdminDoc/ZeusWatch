package com.sysadmindoc.nimbus.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import java.util.Locale

/**
 * Shared Glance widget styling constants and helpers.
 * Uses ColorProvider for day/night adaptation.
 * Widgets wrap content in GlanceTheme {} for Material You support.
 */
object WidgetTheme {
    // Dark background matching app theme
    val bgColor = ColorProvider(
        day = Color(0xFF0F1526),
        night = Color(0xFF0A0E1A),
    )
    val cardColor = ColorProvider(
        day = Color(0xFF1B2845),
        night = Color(0xFF111833),
    )
    val cardColorElevated = ColorProvider(
        day = Color(0xFF23365D),
        night = Color(0xFF172546),
    )
    val pillColor = ColorProvider(
        day = Color(0xFF162743),
        night = Color(0xFF101A31),
    )
    val textPrimary = ColorProvider(
        day = Color(0xFFF0F0F5),
        night = Color(0xFFF0F0F5),
    )
    val textSecondary = ColorProvider(
        day = Color(0xFFB0B8CC),
        night = Color(0xFFB0B8CC),
    )
    val textTertiary = ColorProvider(
        day = Color(0xFF7A839E),
        night = Color(0xFF7A839E),
    )
    val accentBlue = ColorProvider(
        day = Color(0xFF3D6CB9),
        night = Color(0xFF3D6CB9),
    )
    val precipBlue = ColorProvider(
        day = Color(0xFF64B5F6),
        night = Color(0xFF64B5F6),
    )

    val tempLarge = TextStyle(
        color = textPrimary,
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
    )
    val tempMedium = TextStyle(
        color = textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )
    val tempSmall = TextStyle(
        color = textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    )
    val labelStyle = TextStyle(
        color = textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
    )
    val eyebrowStyle = TextStyle(
        color = accentBlue,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
    )
    val titleStyle = TextStyle(
        color = textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
    val locationStyle = TextStyle(
        color = textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    val highLowStyle = TextStyle(
        color = textTertiary,
        fontSize = 12.sp,
    )
    val precipStyle = TextStyle(
        color = precipBlue,
        fontSize = 10.sp,
    )
    val captionStyle = TextStyle(
        color = textTertiary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
    )
    val pillStyle = TextStyle(
        color = accentBlue,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
    )
}

fun widgetUpdatedLabel(
    updatedAt: Long,
    liveLabel: String = "Live",
    minuteFormat: String = "%1\$dm",
    hourFormat: String = "%1\$dh",
): String? {
    if (updatedAt <= 0L) return null
    val mins = ((System.currentTimeMillis() - updatedAt) / 60_000).coerceAtLeast(0L)
    return when {
        mins < 5 -> liveLabel
        mins < 60 -> String.format(Locale.getDefault(), minuteFormat, mins)
        else -> String.format(Locale.getDefault(), hourFormat, mins / 60)
    }
}

@Composable
fun WidgetPill(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    onClick: Action? = null,
    contentDescription: String? = null,
) {
    val base = modifier
        .cornerRadius(8.dp)
        .background(WidgetTheme.pillColor)
        .padding(horizontal = 8.dp, vertical = 4.dp)
    val withSemantics = if (contentDescription != null) {
        base.semantics { this.contentDescription = contentDescription }
    } else {
        base
    }
    val final = if (onClick != null) withSemantics.clickable(onClick) else withSemantics
    Box(
        modifier = final,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = WidgetTheme.pillStyle,
            maxLines = 1,
        )
    }
}

/**
 * Convenience factory for a freshness pill that forces an immediate
 * refresh when tapped. Prefer over plain [WidgetPill] on data-loaded
 * widgets so users can force-refresh without opening the app.
 */
@Composable
fun widgetRefreshPillAction(): Action = actionRunCallback<WidgetRefreshAction>()

@Composable
fun WidgetEmptyState(
    title: String,
    message: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = WidgetTheme.titleStyle,
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = message,
            style = WidgetTheme.labelStyle,
            maxLines = 2,
        )
    }
}

/**
 * Map WMO weather code to drawable resource for Glance widgets.
 * Covers all major condition groups with day/night variants for clear skies.
 */
fun weatherIconRes(code: Int, isDay: Boolean): Int = when {
    code == 0 || code == 1 -> if (isDay) R.drawable.ic_w_sunny else R.drawable.ic_w_night
    code in 2..3 -> R.drawable.ic_w_cloudy
    code in 45..48 -> R.drawable.ic_w_cloudy // fog
    code in 51..57 -> R.drawable.ic_w_rain   // drizzle
    code in 61..67 -> R.drawable.ic_w_rain   // rain + freezing rain
    code in 71..77 -> R.drawable.ic_w_snow   // snow + snow grains
    code in 80..82 -> R.drawable.ic_w_rain   // rain showers
    code in 85..86 -> R.drawable.ic_w_snow   // snow showers
    code in 95..99 -> R.drawable.ic_w_rain   // thunderstorms
    else -> R.drawable.ic_w_cloudy
}
