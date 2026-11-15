package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** The current-conditions hero draws its text straight on the sky gradient, with no card. */
class HeroSkyContrastTest {

    @Test
    fun heroInksReadOnEveryShadeOfEverySky() {
        palettes().forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            val inks = listOf(
                "secondary" to palette.textSecondary,
                "warmer trend" to palette.warning,
                "cooler trend" to palette.rainTint,
            )
            skies().forEach { (code, isDay) ->
                val shades = sampled(palette.skyStops(isDay, code))
                inks.forEach { (name, ink) ->
                    val drawn = palette.skyTint(ink, isDay, code)
                    shades.forEach { shade ->
                        val ratio = wcag(drawn, shade)
                        assertTrue(
                            "[$theme] $name ${drawn.hex()} on sky $code ${if (isDay) "day" else "night"} " +
                                "shade ${shade.hex()} reads ${"%.2f".format(ratio)}",
                            ratio >= 4.5,
                        )
                    }
                }
                shades.forEach { shade ->
                    assertTrue(
                        "[$theme] primary on ${shade.hex()} reads below 4.5",
                        wcag(palette.textPrimary, shade) >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun skyTintKeepsTheHueSoWarmerAndCoolerStayApart() {
        palettes().forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            skies().forEach { (code, isDay) ->
                val warmer = palette.skyTint(palette.warning, isDay, code)
                val cooler = palette.skyTint(palette.rainTint, isDay, code)
                listOf(palette.warning to warmer, palette.rainTint to cooler).forEach { (raw, tinted) ->
                    val drift = hueGap(raw.toHsl().hue, tinted.toHsl().hue)
                    assertTrue("sky $code moved ${raw.hex()} by $drift degrees", drift <= 8f)
                    // Paler or darker shades lose colour; under about 10 a
                    // shade reads as grey and the trend's colour cue is gone.
                    assertTrue(
                        "[$theme] sky $code ${tinted.hex()} chroma ${"%.1f".format(tinted.lab().chroma())}",
                        tinted.lab().chroma() >= 10.0,
                    )
                }
                // 20 apart in CIELAB is two plainly different colours at a glance.
                val gap = deltaE(warmer.lab(), cooler.lab())
                assertTrue(
                    "[$theme] sky $code warmer ${warmer.hex()} and cooler ${cooler.hex()} only ${"%.1f".format(gap)} apart",
                    gap >= 20.0,
                )
            }
        }
    }

    @Test
    fun skyTintOnlyMovesAnInkOnSkiesWhereItFails() {
        val dark = NimbusPalette.Dark
        // The dark rain sky peaks at #2D4B73, where the secondary ink already reads 4.98:1.
        assertEquals(dark.textSecondary, dark.skyTint(dark.textSecondary, isDay = true, weatherCode = 61))
        // The dark clear-day sky peaks at #34689D, where it reads 3.26:1.
        assertNotEquals(dark.textSecondary, dark.skyTint(dark.textSecondary, isDay = true, weatherCode = 0))
        assertEquals(dark.textPrimary, dark.skyTint(dark.textPrimary, isDay = true, weatherCode = 0))
    }

    private fun palettes() = listOf(NimbusPalette.Light, NimbusPalette.Dark)

    private fun skies(): List<Pair<Int, Boolean>> =
        listOf(0, 1, 2, 3, 45, 48, 51, 61, 71, 80, 95).flatMap { code -> listOf(code to true, code to false) }

    /** Each stop and 15 points between every pair, mixed per sRGB channel like the drawn gradient. */
    private fun sampled(stops: List<Color>): List<Color> = stops.zipWithNext().flatMap { (a, b) ->
        (0..16).map { i ->
            val t = i / 16f
            Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
        }
    }

    private fun hueGap(a: Float, b: Float): Float = abs(a - b).let { minOf(it, 360f - it) }

    private fun Color.hex(): String = "#%08X".format(value.shr(32).toLong())

    private fun wcag(a: Color, b: Color): Double {
        val x = a.luminanceOf()
        val y = b.luminanceOf()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun Color.luminanceOf(): Double =
        0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)

    private fun linear(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    /** CIELAB under D65, from sRGB. */
    private fun Color.lab(): DoubleArray {
        val r = linear(red)
        val g = linear(green)
        val b = linear(blue)
        fun f(t: Double): Double = if (t > 216.0 / 24389.0) cbrt(t) else t * 24389.0 / 3132.0 + 4.0 / 29.0
        val fx = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
        val fy = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
        val fz = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun DoubleArray.chroma(): Double = hypot(this[1], this[2])

    private fun deltaE(a: DoubleArray, b: DoubleArray): Double =
        sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))
}
