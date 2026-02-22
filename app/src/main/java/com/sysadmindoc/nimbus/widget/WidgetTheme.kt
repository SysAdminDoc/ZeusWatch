package com.sysadmindoc.nimbus.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Image
import androidx.glance.ColorFilter
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.MainActivity
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.WeatherCode

/**
 * Shared Glance widget styling constants and helpers.
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
}

/**
 * Map WMO weather code to drawable resource for Glance widgets.
 */
fun weatherIconRes(code: Int, isDay: Boolean): Int = when {
    code == 0 || code == 1 -> if (isDay) R.drawable.ic_w_sunny else R.drawable.ic_w_night
    code in 2..3 -> R.drawable.ic_w_cloudy
    code in 45..48 -> R.drawable.ic_w_cloudy // fog
    code in 51..67 -> R.drawable.ic_w_rain
    code in 71..77 -> R.drawable.ic_w_snow
    code in 80..82 -> R.drawable.ic_w_rain
    code in 85..86 -> R.drawable.ic_w_snow
    code in 95..99 -> R.drawable.ic_w_rain // storm
    else -> R.drawable.ic_w_cloudy
}
