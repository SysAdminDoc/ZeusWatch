package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary palette - deep navy/indigo (TWC-inspired)
val NimbusNavyDark = Color(0xFF0A0E1A)
val NimbusNavy = Color(0xFF111833)
val NimbusNavyMid = Color(0xFF1B2845)
val NimbusNavyLight = Color(0xFF2D3561)
val NimbusBlueAccent = Color(0xFF3D6CB9)

// Surface colors
val NimbusSurface = Color(0xFF131A2E)
val NimbusSurfaceVariant = Color(0xFF1A2340)
val NimbusSurfaceElevated = Color(0xFF1F2B4A)

// Card overlay (semi-transparent white on dark gradient)
val NimbusCardBg = Color(0x1AFFFFFF)       // 10% white
val NimbusCardBgHover = Color(0x26FFFFFF)   // 15% white
val NimbusCardBorder = Color(0x1AFFFFFF)    // 10% white border

// Text
val NimbusTextPrimary = Color(0xFFF0F0F5)
val NimbusTextSecondary = Color(0xFFB0B8CC)
val NimbusTextTertiary = Color(0xFF7A839E)

// Accent colors for weather conditions
val NimbusSunYellow = Color(0xFFFFD54F)
val NimbusMoonBlue = Color(0xFFC5CAE9)
val NimbusRainBlue = Color(0xFF64B5F6)
val NimbusSnowWhite = Color(0xFFE8EAF6)
val NimbusStormPurple = Color(0xFFAB47BC)
val NimbusFogGray = Color(0xFF90A4AE)

// Data visualization colors
val NimbusUvLow = Color(0xFF4CAF50)
val NimbusUvModerate = Color(0xFFFFEB3B)
val NimbusUvHigh = Color(0xFFFF9800)
val NimbusUvVeryHigh = Color(0xFFF44336)
val NimbusUvExtreme = Color(0xFF9C27B0)

// Precipitation probability gradient
val NimbusPrecipLow = Color(0xFF2196F3)
val NimbusPrecipHigh = Color(0xFF1565C0)

// Status colors
val NimbusError = Color(0xFFCF6679)
val NimbusWarning = Color(0xFFFFB74D)
val NimbusSuccess = Color(0xFF66BB6A)

// Background gradients
val NimbusBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        NimbusNavyDark,
        NimbusNavy,
        NimbusNavyMid,
        NimbusNavyDark,
    )
)

val NimbusHeaderGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0D1526),
        Color(0xFF152040),
        Color(0xFF1B2845),
    )
)

// Condition-based sky gradients
fun skyGradient(isDay: Boolean, weatherCode: Int): Brush {
    val colors = when {
        weatherCode >= 95 -> listOf(Color(0xFF1A1A2E), Color(0xFF2D1B4E), Color(0xFF1A1A2E))
        weatherCode >= 61 -> listOf(Color(0xFF0D1526), Color(0xFF1B2845), Color(0xFF263859))
        weatherCode >= 45 -> listOf(Color(0xFF1A1A2E), Color(0xFF2A2A3E), Color(0xFF1A1A2E))
        weatherCode >= 2 -> if (isDay)
            listOf(Color(0xFF0F1B33), Color(0xFF1B3054), Color(0xFF1B2845))
        else
            listOf(Color(0xFF0A0E1A), Color(0xFF111833), Color(0xFF0A0E1A))
        isDay -> listOf(Color(0xFF0D1940), Color(0xFF1B3570), Color(0xFF1B2845))
        else -> listOf(Color(0xFF050810), Color(0xFF0A1020), Color(0xFF050810))
    }
    return Brush.verticalGradient(colors)
}
