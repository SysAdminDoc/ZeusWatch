package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Precision atmospheric palette
val NimbusNavyDark = Color(0xFF01070D)
val NimbusNavy = Color(0xFF07111B)
val NimbusNavyMid = Color(0xFF0C1C2B)
val NimbusNavyLight = Color(0xFF173A5C)
val NimbusBlueAccent = Color(0xFF54A8FF)
val NimbusBlueAccentSoft = Color(0xFFA9D3FF)

// Surface colors
val NimbusSurface = Color(0xFF07121C)
val NimbusSurfaceVariant = Color(0xFF0B1A27)
val NimbusSurfaceElevated = Color(0xFF102538)
val NimbusToolbarSurface = Color(0xE607111B)
val NimbusNavSurface = Color(0xFA030B13)

// Glass surfaces and highlights
val NimbusCardBg = Color(0xB30A1723)
val NimbusCardBgHover = Color(0xD90D2030)
val NimbusCardBorder = Color(0x6630475B)
val NimbusGlassTop = Color(0x9910202F)
val NimbusGlassBottom = Color(0xE6030C15)
val NimbusGlassHighlight = Color(0x335B8CB8)
val NimbusHeroGlow = Color(0x3654A8FF)
val NimbusHeroGlowSoft = Color(0x1F245B88)

// Text
val NimbusTextPrimary = Color(0xFFF4F7FB)
val NimbusTextSecondary = Color(0xFFB7C3D4)
val NimbusTextTertiary = Color(0xFF8798AD)

// Accent colors for weather conditions
val NimbusSunYellow = Color(0xFFFFD54F)
val NimbusMoonBlue = Color(0xFFC5CAE9)
val NimbusRainBlue = Color(0xFF73C5FF)
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
        Color(0xFF01070D),
        Color(0xFF06131E),
        Color(0xFF081925),
        Color(0xFF020A11),
    )
)

val NimbusHeaderGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF07131F),
        Color(0xFF0D2233),
        Color(0xFF071724),
    )
)

// Condition-based sky gradients
fun skyGradient(isDay: Boolean, weatherCode: Int): Brush {
    val colors = when {
        weatherCode >= 95 -> listOf(Color(0xFF090B17), Color(0xFF24193F), Color(0xFF13223F), Color(0xFF070B15))
        weatherCode >= 61 -> listOf(Color(0xFF09111F), Color(0xFF143255), Color(0xFF2D4B73), Color(0xFF08101D))
        weatherCode >= 45 -> listOf(Color(0xFF101522), Color(0xFF293244), Color(0xFF202B3C), Color(0xFF0B1019))
        weatherCode >= 2 -> if (isDay)
            listOf(Color(0xFF0C1A32), Color(0xFF17355E), Color(0xFF24507A), Color(0xFF0E1830))
        else
            listOf(Color(0xFF040813), Color(0xFF0D1730), Color(0xFF142648), Color(0xFF050913))
        isDay -> listOf(Color(0xFF10224A), Color(0xFF1E4A8D), Color(0xFF34689D), Color(0xFF11213E))
        else -> listOf(Color(0xFF040813), Color(0xFF0A1224), Color(0xFF12254C), Color(0xFF050A14))
    }
    return Brush.verticalGradient(colors)
}
