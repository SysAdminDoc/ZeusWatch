package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme-aware chrome tokens: the surfaces, glass fills, borders and text inks
 * that [Color.kt] exposes as composable constants. Dark values mirror the
 * original precision-navy palette 1:1; light values are the normal light
 * theme (issue #59).
 *
 * The public constants in [Color.kt] delegate to the palette provided by
 * [NimbusTheme], so call sites stay untouched when the mode switches.
 * Accent and data-visualization colors are deliberately NOT in here — they
 * carry meaning (UV scale, condition icons) that must not shift with the
 * theme, and [inkOn] adapts their ink automatically.
 */
data class NimbusPalette(
    val isLight: Boolean,
    val background: Color,
    val chrome: Color,
    val navyMid: Color,
    val navyLight: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val toolbarSurface: Color,
    val navSurface: Color,
    val cardBg: Color,
    val cardBgHover: Color,
    val cardBorder: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassHighlight: Color,
    val heroGlow: Color,
    val heroGlowSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    /** Alert banner sheet background (issue inventory: AlertBanner 0xFF111833). */
    val overlayCard: Color,
    /** Snow accent readable on cards in both modes (condition icons, hourly tints). */
    val snowTint: Color,
) {
    fun backgroundGradient(): Brush = if (isLight) {
        Brush.verticalGradient(
            listOf(
                Color(0xFFF3F6FA),
                Color(0xFFEAF1F8),
                Color(0xFFE4EDF6),
                Color(0xFFF6F9FC),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF01070D),
                Color(0xFF06131E),
                Color(0xFF081925),
                Color(0xFF020A11),
            )
        )
    }

    fun headerGradient(): Brush = if (isLight) {
        Brush.verticalGradient(
            listOf(
                Color(0xFFF2F7FC),
                Color(0xFFE7F0F9),
                Color(0xFFF1F6FB),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF07131F),
                Color(0xFF0D2233),
                Color(0xFF071724),
            )
        )
    }

    /**
     * Full-screen sky wash. Light mode keeps a hint of the condition mood
     * (stormier = greyer, clear day = bluest) without ever leaving light
     * surfaces, so cards and text tokens stay readable.
     */
    fun skyGradient(isDay: Boolean, weatherCode: Int): Brush = if (isLight) {
        val colors = when {
            weatherCode >= 95 -> listOf(Color(0xFFD9DCE8), Color(0xFFC9CEE0), Color(0xFFD3D9E8), Color(0xFFE2E5EF))
            weatherCode >= 61 -> listOf(Color(0xFFD3E0EF), Color(0xFFC4D6EA), Color(0xFFCFDEF0), Color(0xFFDEE9F4))
            weatherCode >= 45 -> listOf(Color(0xFFE3E7EC), Color(0xFFD8DDE4), Color(0xFFDDE2E9), Color(0xFFEBEEF2))
            weatherCode >= 2 -> if (isDay)
                listOf(Color(0xFFDCE8F5), Color(0xFFD0DEF0), Color(0xFFD8E4F2), Color(0xFFE6EEF8))
            else
                listOf(Color(0xFFD5DCE6), Color(0xFFCBD4E0), Color(0xFFD2DAE6), Color(0xFFE0E6EE))
            isDay -> listOf(Color(0xFFCFE3F7), Color(0xFFBFD8F2), Color(0xFFCCE0F5), Color(0xFFDCEAF8))
            else -> listOf(Color(0xFFD3DCE8), Color(0xFFC8D3E2), Color(0xFFCFD9E7), Color(0xFFDDE5EF))
        }
        Brush.verticalGradient(colors)
    } else {
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
        Brush.verticalGradient(colors)
    }

    companion object {
        /** The original precision-navy palette, byte-for-byte. */
        val Dark = NimbusPalette(
            isLight = false,
            background = Color(0xFF01070D),
            chrome = Color(0xFF07111B),
            navyMid = Color(0xFF0C1C2B),
            navyLight = Color(0xFF173A5C),
            surface = Color(0xFF07121C),
            surfaceVariant = Color(0xFF0B1A27),
            surfaceElevated = Color(0xFF102538),
            toolbarSurface = Color(0xE607111B),
            navSurface = Color(0xFA030B13),
            cardBg = Color(0xB30A1723),
            cardBgHover = Color(0xD90D2030),
            cardBorder = Color(0x6630475B),
            glassTop = Color(0x9910202F),
            glassBottom = Color(0xE6030C15),
            glassHighlight = Color(0x335B8CB8),
            heroGlow = Color(0x3654A8FF),
            heroGlowSoft = Color(0x1F245B88),
            textPrimary = Color(0xFFF4F7FB),
            textSecondary = Color(0xFFB7C3D4),
            textTertiary = Color(0xFF8798AD),
            error = Color(0xFFCF6679),
            warning = Color(0xFFFFB74D),
            success = Color(0xFF66BB6A),
            overlayCard = Color(0xFF111833),
            snowTint = Color(0xFFE8EAF6),
        )

        /** Normal light theme: white cards on a cool paper background (issue #59). */
        val Light = NimbusPalette(
            isLight = true,
            background = Color(0xFFF3F6FA),
            chrome = Color(0xFFF8FAFC),
            navyMid = Color(0xFFDCE6F0),
            navyLight = Color(0xFFD3E5FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EEF5),
            surfaceElevated = Color(0xFFFFFFFF),
            toolbarSurface = Color(0xF2FFFFFF),
            navSurface = Color(0xFAFFFFFF),
            cardBg = Color(0xD9FFFFFF),
            cardBgHover = Color(0xE6FFFFFF),
            cardBorder = Color(0x3390A4AE),
            glassTop = Color(0xB3FFFFFF),
            glassBottom = Color(0xF2FFFFFF),
            glassHighlight = Color(0x331E88C8),
            heroGlow = Color(0x330A66C2),
            heroGlowSoft = Color(0x1F9EB8D6),
            textPrimary = Color(0xFF16202B),
            textSecondary = Color(0xFF44566B),
            textTertiary = Color(0xFF5F7186),
            error = Color(0xFFB3261E),
            warning = Color(0xFF8F5F00),
            success = Color(0xFF1E7B34),
            overlayCard = Color(0xFFE9F0FA),
            snowTint = Color(0xFF5A6B85),
        )
    }
}

val LocalNimbusPalette = staticCompositionLocalOf { NimbusPalette.Dark }
