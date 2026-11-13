package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Precision atmospheric palette.
 *
 * The chrome constants below are **theme-aware**: each one is a composable
 * getter over [LocalNimbusPalette], provided by [NimbusTheme] from the user's
 * theme mode (issue #59). In dark mode they resolve to the original navy
 * values in [NimbusPalette.Dark]; in the new light mode to [NimbusPalette.Light].
 * Call sites read them exactly as before — the only rule is that they must be
 * read from composable context (hoist into a `val` before a `Canvas`/
 * `remember` block if you need them inside one).
 *
 * Accent and data-visualization colors stay literal on purpose: their meaning
 * (UV scale, condition icons, precipitation gradient) must not shift with the
 * theme, and [inkOn] picks readable ink on top of them.
 */

// Fixed raw inks: theme-independent luminance reference points for the
// contrast math in inkOn()/accentOnColor(), which must not recurse into the
// palette getters (they are called from non-composable code).
internal val NimbusRawInkLight = Color(0xFFF4F7FB) // == NimbusPalette.Dark.textPrimary
internal val NimbusRawInkDark = Color(0xFF01070D) // == NimbusPalette.Dark.background

val NimbusNavyDark: Color
    @Composable get() = LocalNimbusPalette.current.background
val NimbusNavy: Color
    @Composable get() = LocalNimbusPalette.current.chrome
val NimbusNavyMid: Color
    @Composable get() = LocalNimbusPalette.current.navyMid
val NimbusNavyLight: Color
    @Composable get() = LocalNimbusPalette.current.navyLight
val NimbusBlueAccent = Color(0xFF54A8FF)
val NimbusBlueAccentSoft = Color(0xFFA9D3FF)

// Surface colors
val NimbusSurface: Color
    @Composable get() = LocalNimbusPalette.current.surface
val NimbusSurfaceVariant: Color
    @Composable get() = LocalNimbusPalette.current.surfaceVariant
val NimbusSurfaceElevated: Color
    @Composable get() = LocalNimbusPalette.current.surfaceElevated
val NimbusToolbarSurface: Color
    @Composable get() = LocalNimbusPalette.current.toolbarSurface
val NimbusNavSurface: Color
    @Composable get() = LocalNimbusPalette.current.navSurface

// Glass surfaces and highlights
val NimbusCardBg: Color
    @Composable get() = LocalNimbusPalette.current.cardBg
val NimbusCardBgHover: Color
    @Composable get() = LocalNimbusPalette.current.cardBgHover
val NimbusCardBorder: Color
    @Composable get() = LocalNimbusPalette.current.cardBorder
val NimbusGlassTop: Color
    @Composable get() = LocalNimbusPalette.current.glassTop
val NimbusGlassBottom: Color
    @Composable get() = LocalNimbusPalette.current.glassBottom
val NimbusGlassHighlight: Color
    @Composable get() = LocalNimbusPalette.current.glassHighlight
val NimbusHeroGlow: Color
    @Composable get() = LocalNimbusPalette.current.heroGlow
val NimbusHeroGlowSoft: Color
    @Composable get() = LocalNimbusPalette.current.heroGlowSoft

// Text
val NimbusTextPrimary: Color
    @Composable get() = LocalNimbusPalette.current.textPrimary

/**
 * Ink for text sitting on one of the bright accent fills.
 *
 * Filled buttons took the accent as their container and NimbusTextPrimary as
 * their label, which is near-white on near-white: Retry read at 1.61:1 on the
 * amber fill and the location actions at 2.32:1 on the blue. Against those two
 * fills this reaches 10.1:1 and 7.0:1.
 */
val NimbusInkOnAccent = Color(0xFF0A1A2F)
val NimbusTextSecondary: Color
    @Composable get() = LocalNimbusPalette.current.textSecondary
val NimbusTextTertiary: Color
    @Composable get() = LocalNimbusPalette.current.textTertiary

// Status colors — darkened in light mode so they stay readable on white cards.
val NimbusError: Color
    @Composable get() = LocalNimbusPalette.current.error
val NimbusWarning: Color
    @Composable get() = LocalNimbusPalette.current.warning
val NimbusSuccess: Color
    @Composable get() = LocalNimbusPalette.current.success

// Accent colors for weather conditions. NimbusSnowWhite is the one exception
// that tracks the palette: pale lavender snow is invisible on light cards.
val NimbusSunYellow = Color(0xFFFFD54F)
val NimbusMoonBlue = Color(0xFFC5CAE9)
val NimbusRainBlue = Color(0xFF73C5FF)
val NimbusSnowWhite: Color
    @Composable get() = LocalNimbusPalette.current.snowTint
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

// Background gradients
val NimbusBackgroundGradient: Brush
    @Composable get() = LocalNimbusPalette.current.backgroundGradient()

val NimbusHeaderGradient: Brush
    @Composable get() = LocalNimbusPalette.current.headerGradient()

// Condition-based sky gradients
@Composable
fun skyGradient(isDay: Boolean, weatherCode: Int): Brush =
    LocalNimbusPalette.current.skyGradient(isDay, weatherCode)

/**
 * Readable ink for text drawn on [container].
 *
 * Filled controls take a caller-chosen accent as their fill, and hardcoding a
 * near-white label put "Retry" at 1.61:1 on the amber fill. Choosing by the
 * fill's own luminance keeps every accent readable, including ones added later.
 * The light ink is fixed (not [NimbusTextPrimary]): dark containers stay
 * dark in both modes, so their ink stays near-white.
 */
fun inkOn(container: Color): Color =
    if (container.luminance() > LIGHT_INK_CROSSOVER) NimbusInkOnAccent else NimbusRawInkLight

/**
 * Relative luminance at which dark ink overtakes light ink on the same fill.
 *
 * 0.4 was the first guess and it was wrong for the blue accent, which sits at
 * 0.357: bright enough that near-white on it reads 2.32:1, dark enough to fall
 * under the threshold and keep the near-white.
 */
private const val LIGHT_INK_CROSSOVER = 0.179f
