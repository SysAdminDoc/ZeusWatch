package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.roundToInt

/** WCAG AA for body text. */
internal const val WCAG_TEXT_RATIO = 4.5

/** WCAG AA for bars, arcs, dots, icons and large text. */
internal const val WCAG_NON_TEXT_RATIO = 3.0

/**
 * [color] moved along its own HSL lightness until it reads at [minimumRatio]
 * on every one of [grounds]: darker when [darken], lighter otherwise. Hue and
 * saturation stay. A colour that already passes, with [RATIO_MARGIN] to
 * spare, comes back unchanged.
 *
 * Neighbouring steps of one hue that both need moving can land on one shade
 * (as text on white, Orange 200, 300 and 500 all end at the 4.5:1 line), so
 * a scale that colours text names its steps in the text too. Bars whose
 * shade is the only cue use [NimbusPalette.stepTint] instead.
 *
 * [fade] is the alpha the caller draws the colour at. The faded colour is
 * what gets judged, laid over each ground, and the result carries the fade
 * on top of the colour's own alpha. A fade too faint for even black or white
 * to reach the ratio returns that far end, the best there is.
 */
internal fun readableOn(
    color: Color,
    grounds: List<Color>,
    minimumRatio: Double,
    darken: Boolean,
    fade: Float = 1f,
): Color {
    val alpha = color.alpha * fade
    val opaque = color.copy(alpha = 1f)
    if (readsOnAll(opaque, grounds, minimumRatio, fade)) return color.copy(alpha = alpha)
    val (hue, saturation, lightness) = opaque.toHsl()
    return searchLightness(hue, saturation, lightness, grounds, minimumRatio, darken, fade).copy(alpha = alpha)
}

/**
 * The shade of [hue] and [saturation] nearest the ground that still clears
 * [minimumRatio], with [RATIO_MARGIN] to spare like everything [readableOn]
 * judges, on every one of [grounds], searching from [from] toward the
 * far end (black when [darken], white otherwise). Black clears 4.5:1 on every
 * light card and white on every dark one, so for an opaque colour the far end
 * always passes. Each probe is quantized to 8 bits first, because that is
 * what gets drawn, and a float that passes can round to one that doesn't.
 */
internal fun searchLightness(
    hue: Float,
    saturation: Float,
    from: Float,
    grounds: List<Color>,
    minimumRatio: Double,
    darken: Boolean,
    fade: Float = 1f,
): Color {
    var passing = if (darken) 0f else 1f
    if (!readsOnAll(hslToDrawn(hue, saturation, passing), grounds, minimumRatio, fade)) {
        return hslToDrawn(hue, saturation, passing)
    }
    var failing = from
    repeat(SEARCH_STEPS) {
        val mid = (passing + failing) / 2f
        if (readsOnAll(hslToDrawn(hue, saturation, mid), grounds, minimumRatio, fade)) {
            passing = mid
        } else {
            failing = mid
        }
    }
    return hslToDrawn(hue, saturation, passing)
}

/**
 * Judged a hair above [minimumRatio]: the screen rounds every translucent
 * layer it stacks to 8 bits in turn, which can land a colour found at exactly
 * the line just under it.
 */
private fun readsOnAll(opaque: Color, grounds: List<Color>, minimumRatio: Double, fade: Float): Boolean =
    grounds.all { ground -> contrastRatio(fadedOver(opaque, fade, ground), ground) >= minimumRatio + RATIO_MARGIN }

/** [opaque] drawn at [fade] over [ground], rounded to 8 bits like the screen. */
internal fun fadedOver(opaque: Color, fade: Float, ground: Color): Color =
    if (fade >= 1f) opaque else quantize(opaque.copy(alpha = fade).compositeOver(ground))

/** WCAG contrast ratio between two opaque colours. */
internal fun contrastRatio(first: Color, second: Color): Double {
    val a = first.luminance().toDouble()
    val b = second.luminance().toDouble()
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

private fun hslToDrawn(hue: Float, saturation: Float, lightness: Float): Color =
    quantize(Color.hsl(hue, saturation, lightness.coerceIn(0f, 1f)))

private fun quantize(c: Color): Color = Color(
    red = (c.red * 255f).roundToInt(),
    green = (c.green * 255f).roundToInt(),
    blue = (c.blue * 255f).roundToInt(),
)

internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

internal fun Color.toHsl(): Hsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta == 0f) return Hsl(0f, 0f, lightness)
    val saturation = (delta / (1f - abs(2f * lightness - 1f))).coerceIn(0f, 1f)
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }.coerceIn(0f, 359.999f)
    return Hsl(hue, saturation, lightness)
}

private const val SEARCH_STEPS = 20

private const val RATIO_MARGIN = 0.05
