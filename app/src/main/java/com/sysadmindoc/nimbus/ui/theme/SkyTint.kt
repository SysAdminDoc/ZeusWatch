package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * [color] as text straight on the sky drawn for [weatherCode] by day or
 * night, at 4.5:1 on every shade of it. The sky is one fixed gradient behind
 * the scrolling list, so the hero can sit on any part of it. An ink that
 * already reads comes back unchanged. One that doesn't moves to a shade of its
 * own hue that does: paler on dark skies (the secondary and trend inks on the
 * clear day, rain blue on cloudy days), darker on light ones (the warmer
 * trend's orange on every light sky). Particles drawn behind the hero keep
 * clear of its text (`particlesBehind`), so the sky is all that's under it.
 */
fun NimbusPalette.skyTint(color: Color, isDay: Boolean, weatherCode: Int): Color =
    readableOn(color, skyShades(isDay, weatherCode), WCAG_TEXT_RATIO, darken = isLight)

/** [NimbusPalette.skyTint] on the current palette. */
@Composable
fun skyTint(color: Color, isDay: Boolean, weatherCode: Int): Color {
    val palette = LocalNimbusPalette.current
    return remember(palette, color, isDay, weatherCode) { palette.skyTint(color, isDay, weatherCode) }
}

/**
 * The sky gradient sampled between each pair of its stops, mixed channel by
 * channel in sRGB the way the gradient is drawn. A mix of two stops can sit
 * darker than both, so the stops alone aren't enough.
 */
internal fun NimbusPalette.skyShades(isDay: Boolean, weatherCode: Int): List<Color> =
    skyStops(isDay, weatherCode).zipWithNext().flatMap { (from, to) ->
        (0..SKY_SAMPLES).map { i ->
            val t = i / SKY_SAMPLES.toFloat()
            Color(
                red = from.red + (to.red - from.red) * t,
                green = from.green + (to.green - from.green) * t,
                blue = from.blue + (to.blue - from.blue) * t,
            )
        }
    }

private const val SKY_SAMPLES = 8
