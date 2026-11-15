package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.ui.component.CONFIDENCE_FILL_ALPHA
import com.sysadmindoc.nimbus.ui.component.NORMAL_BAND_ALPHA
import com.sysadmindoc.nimbus.ui.component.PRECIP_BAR_ALPHA
import com.sysadmindoc.nimbus.ui.component.TEMPERATURE_FILL_ALPHA
import com.sysadmindoc.nimbus.ui.component.temperatureGraphFills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class AccessibilityContrastTest {

    @Test
    fun textTokensMeetWcagAaContrastOnPrimarySurfaces() {
        val dark = NimbusPalette.Dark
        val cardBackground = dark.cardBg.compositeOver(dark.background)
        val pairs = listOf(
            ContrastPair("primary on navy", dark.textPrimary, dark.background, WCAG_AA_TEXT),
            ContrastPair("secondary on navy", dark.textSecondary, dark.background, WCAG_AA_TEXT),
            ContrastPair("tertiary on navy", dark.textTertiary, dark.background, WCAG_AA_TEXT),
            ContrastPair("primary on surface", dark.textPrimary, dark.surface, WCAG_AA_TEXT),
            ContrastPair("secondary on surface", dark.textSecondary, dark.surface, WCAG_AA_TEXT),
            ContrastPair("tertiary on surface", dark.textTertiary, dark.surface, WCAG_AA_TEXT),
            ContrastPair("primary on card", dark.textPrimary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("secondary on card", dark.textSecondary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("tertiary on card", dark.textTertiary, cardBackground, WCAG_AA_TEXT),
        )

        pairs.assertContrast()
    }

    @Test
    fun lightThemeTextTokensMeetWcagAaContrastOnPrimarySurfaces() {
        val light = NimbusPalette.Light
        val cardBackground = light.cardBg.compositeOver(light.background)
        val pairs = listOf(
            ContrastPair("light primary on background", light.textPrimary, light.background, WCAG_AA_TEXT),
            ContrastPair("light secondary on background", light.textSecondary, light.background, WCAG_AA_TEXT),
            ContrastPair("light tertiary on background", light.textTertiary, light.background, WCAG_AA_TEXT),
            ContrastPair("light primary on surface", light.textPrimary, light.surface, WCAG_AA_TEXT),
            ContrastPair("light secondary on surface", light.textSecondary, light.surface, WCAG_AA_TEXT),
            ContrastPair("light tertiary on surface", light.textTertiary, light.surface, WCAG_AA_TEXT),
            ContrastPair("light primary on card", light.textPrimary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("light secondary on card", light.textSecondary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("light tertiary on card", light.textTertiary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("light rain text on background", light.rainTint, light.background, WCAG_AA_TEXT),
            ContrastPair("light rain text on surface", light.rainTint, light.surface, WCAG_AA_TEXT),
            ContrastPair("light rain text on card", light.rainTint, cardBackground, WCAG_AA_TEXT),
            // The hero trend label sits on the sky wash; storm is its darkest light stop.
            ContrastPair("light rain text on storm sky", light.rainTint, Color(0xFFC9CEE0), WCAG_AA_TEXT),
        )

        pairs.assertContrast()
    }

    @Test
    fun lightAccentTextMeetsWcagAaOnCardsAndItsOwnChipWashes() {
        val light = NimbusPalette.Light
        val cardBackground = light.cardBg.compositeOver(light.background)
        // A translucent card over the darkest light sky stop (storm).
        val cardOnStormSky = light.cardBg.compositeOver(Color(0xFFC9CEE0))
        val pairs = listOf(
            ContrastPair("light accent text on background", light.accent, light.background, WCAG_AA_TEXT),
            ContrastPair("light accent text on surface", light.accent, light.surface, WCAG_AA_TEXT),
            ContrastPair("light accent text on card", light.accent, cardBackground, WCAG_AA_TEXT),
            ContrastPair("light accent text on card over storm sky", light.accent, cardOnStormSky, WCAG_AA_TEXT),
            // Selected chips and tabs put accent text on an accent wash (alpha 0.12 to 0.22).
            ContrastPair(
                "light accent text on selected chip",
                light.accent,
                light.accent.copy(alpha = 0.22f).compositeOver(cardOnStormSky),
                WCAG_AA_TEXT,
            ),
            ContrastPair("white ink on light accent fill", Color.White, light.accent, WCAG_AA_TEXT),
        )

        pairs.assertContrast()
    }

    @Test
    fun statusAndAccentTokensMeetNonTextContrastOnPrimarySurfaces() {
        val dark = NimbusPalette.Dark
        val cardBackground = dark.cardBg.compositeOver(dark.background)
        val pairs = listOf(
            ContrastPair("accent on navy", dark.accent, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("warning on navy", dark.warning, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("success on navy", dark.success, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("error on navy", dark.error, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("accent on card", dark.accent, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("warning on card", dark.warning, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("success on card", dark.success, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("error on card", dark.error, cardBackground, WCAG_AA_NON_TEXT),
        )

        pairs.assertContrast()
    }

    @Test
    fun lightThemeStatusTokensMeetNonTextContrastOnPrimarySurfaces() {
        val light = NimbusPalette.Light
        val cardBackground = light.cardBg.compositeOver(light.background)
        val pairs = listOf(
            ContrastPair("light warning on background", light.warning, light.background, WCAG_AA_NON_TEXT),
            ContrastPair("light success on background", light.success, light.background, WCAG_AA_NON_TEXT),
            ContrastPair("light error on background", light.error, light.background, WCAG_AA_NON_TEXT),
            ContrastPair("light warning on card", light.warning, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("light success on card", light.success, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("light error on card", light.error, cardBackground, WCAG_AA_NON_TEXT),
        )

        pairs.assertContrast()
    }

    @Test
    fun lightSchemeOnColorRolesMeetWcagAaTextContrast() {
        val scheme = NimbusLightScheme

        // Judge the tertiary role against its rendered app background.
        val tertiaryBackground = scheme.tertiary.compositeOver(NimbusPalette.Light.background)
        listOf(
            ContrastPair("[light] onPrimary on primary", scheme.onPrimary, scheme.primary, WCAG_AA_TEXT),
            ContrastPair("[light] onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, WCAG_AA_TEXT),
            ContrastPair("[light] onSecondary on secondary", scheme.onSecondary, scheme.secondary, WCAG_AA_TEXT),
            ContrastPair("[light] onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, WCAG_AA_TEXT),
            ContrastPair("[light] onTertiary on tertiary", scheme.onTertiary, tertiaryBackground, WCAG_AA_TEXT),
            ContrastPair("[light] onError on error", scheme.onError, scheme.error, WCAG_AA_TEXT),
        ).assertContrast()
    }

    @Test
    fun onColorRolesMeetWcagAaTextContrastOnEveryScheme() {
        val schemes = buildList {
            add("default" to weatherAdaptiveScheme(weatherCode = null, isDay = true))
            WeatherCode.entries.forEach { code ->
                add("${code.name} day" to weatherAdaptiveScheme(code, isDay = true))
                add("${code.name} night" to weatherAdaptiveScheme(code, isDay = false))
            }
        }

        schemes.flatMap { (name, scheme) ->
            // Judge the tertiary role against its rendered app background.
            val tertiaryBackground = scheme.tertiary.compositeOver(NimbusPalette.Dark.background)
            listOf(
                ContrastPair("[$name] onPrimary on primary", scheme.onPrimary, scheme.primary, WCAG_AA_TEXT),
                ContrastPair("[$name] onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, WCAG_AA_TEXT),
                ContrastPair("[$name] onSecondary on secondary", scheme.onSecondary, scheme.secondary, WCAG_AA_TEXT),
                ContrastPair("[$name] onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, WCAG_AA_TEXT),
                ContrastPair("[$name] onTertiary on tertiary", scheme.onTertiary, tertiaryBackground, WCAG_AA_TEXT),
                ContrastPair("[$name] onError on error", scheme.onError, scheme.error, WCAG_AA_TEXT),
            )
        }.assertContrast()
    }

    @Test
    fun overlayWashStaysVisibleOnCardsInBothModes() {
        // Gauge tracks and chip fills are the overlay ink at about 8% alpha. A
        // white wash on a white light-mode card measures 1.00 and disappears.
        listOf(NimbusPalette.Dark, NimbusPalette.Light).map { palette ->
            val card = palette.cardBg.compositeOver(palette.background)
            val wash = palette.overlayInk.copy(alpha = 0.08f).compositeOver(card)
            ContrastPair(
                if (palette.isLight) "light track on card" else "dark track on card",
                wash,
                card,
                MIN_WASH_SEPARATION,
            )
        }.assertContrast()
    }

    @Test
    fun anyDataColourTintedForCardsReadsInBothThemes() {
        // Grounds from the sky stops themselves, not from the palette helper
        // under test: the card over the darkest light stop (storm) and the
        // brightest dark stop (clear day), each theme's bare background, and
        // the alert banner's sheet, which in light mode is darker than both.
        val grounds = listOf(
            NimbusPalette.Light to listOf(
                "storm-sky card" to NimbusPalette.Light.cardBg.compositeOver(Color(0xFFC9CEE0)),
                "background" to NimbusPalette.Light.background,
                "alert banner" to NimbusPalette.Light.overlayCard,
            ),
            NimbusPalette.Dark to listOf(
                "clear-sky card" to NimbusPalette.Dark.cardBg.compositeOver(Color(0xFF34689D)),
                "background" to NimbusPalette.Dark.background,
                "alert banner" to NimbusPalette.Dark.overlayCard,
            ),
        )
        val pairs = grounds.flatMap { (palette, places) ->
            val theme = if (palette.isLight) "light" else "dark"
            colourSweep().flatMap { color ->
                // Chips put the text on a wash of its own colour.
                val wash = color.copy(alpha = 0.22f)
                places.flatMap { (place, ground) ->
                    listOf(
                        ContrastPair("[$theme] text ${color.hex()} on $place", palette.textTint(color), ground, WCAG_AA_TEXT),
                        ContrastPair("[$theme] mark ${color.hex()} on $place", palette.markTint(color), ground, WCAG_AA_NON_TEXT),
                        ContrastPair(
                            "[$theme] chip text ${color.hex()} on $place",
                            palette.textTint(color, wash),
                            wash.compositeOver(ground),
                            WCAG_AA_TEXT,
                        ),
                    )
                }
            }
        }

        pairs.assertContrast()
    }

    @Test
    fun tintingKeepsTheHueAndLeavesReadableColoursAlone() {
        listOf(NimbusPalette.Light, NimbusPalette.Dark).forEach { palette ->
            colourSweep().forEach { color ->
                val tinted = palette.textTint(color)
                val before = color.toHsl()
                val after = tinted.toHsl()
                // Hue is only meaningful with some saturation and away from
                // black and white, where 8-bit rounding moves it.
                if (before.saturation >= 0.3f && after.lightness in 0.08f..0.92f) {
                    val drift = abs(before.hue - after.hue).let { minOf(it, 360f - it) }
                    assertTrue("${color.hex()} hue moved $drift degrees to ${tinted.hex()}", drift <= 8f)
                }
            }
        }
        // Each theme's own text ink already reads, so it comes back untouched.
        assertEquals(NimbusPalette.Light.textPrimary, NimbusPalette.Light.textTint(NimbusPalette.Light.textPrimary))
        assertEquals(NimbusPalette.Dark.textPrimary, NimbusPalette.Dark.textTint(NimbusPalette.Dark.textPrimary))
        // Orange reads on dark cards (6.4:1 on the clear-sky card) and is left
        // alone there, but moves in light mode, where it reads 2.2:1.
        val orange = Color(0xFFFF9800)
        assertEquals(orange, NimbusPalette.Dark.textTint(orange))
        assertNotEquals(orange, NimbusPalette.Light.textTint(orange))
    }

    @Test
    fun noSkyStopMakesACardHarderThanTheWorstCaseCard() {
        listOf(NimbusPalette.Light, NimbusPalette.Dark).forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            val worst = palette.worstCaseCard.relativeLuminance()
            everySkyStop(palette).forEach { stop ->
                val card = palette.cardBg.compositeOver(stop).relativeLuminance()
                // Light inks are darkened, so a darker card is harder; dark mode the reverse.
                val noHarder = if (palette.isLight) card >= worst - 1e-9 else card <= worst + 1e-9
                assertTrue("[$theme] card over ${stop.hex()} is harder than worstCaseCard", noHarder)
                // The only ink allowed straight on the sky.
                assertTrue(
                    "[$theme] primary text on bare sky ${stop.hex()} reads below 4.5",
                    measuredContrast(palette.textPrimary, stop) >= WCAG_AA_TEXT,
                )
            }
        }
    }

    @Test
    fun fadedMarksReadOnEveryGroundOnceFaded() {
        // The fades the cards draw marks at: wind gust columns, overcast and
        // cloud bars, AQI dots, range and UV bar ends.
        val fades = listOf(0.5f, 0.55f, 0.6f, 0.7f)
        listOf(NimbusPalette.Light, NimbusPalette.Dark).forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            val places = everyGround(palette)
            colourSweep().forEach { color ->
                fades.forEach { fade ->
                    val mark = palette.markTint(color, fade = fade)
                    assertEquals("[$theme] ${color.hex()} lost its fade", fade, mark.alpha, 0.01f)
                    places.forEach { (place, ground) ->
                        val ratio = measuredContrast(drawnOver(mark, ground), ground)
                        assertTrue(
                            "[$theme] ${color.hex()} at $fade on $place reads ${"%.2f".format(ratio)}",
                            ratio >= WCAG_AA_NON_TEXT,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun nowcastStepsReadInOrderAndStayApartInBothThemes() {
        val steps = listOf(
            ScaleColors.NowcastLight to 3.0,
            ScaleColors.NowcastModerate to 4.5,
            ScaleColors.NowcastHeavy to 7.0,
        )
        listOf(NimbusPalette.Light, NimbusPalette.Dark).forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            // The card's own track: the neutral wash on white, an opaque bar in dark.
            val track = if (palette.isLight) palette.overlayInk.copy(alpha = 0.08f) else ScaleColors.NowcastTrack
            val drawn = steps.map { (color, ratio) ->
                palette.stepTint(color, ratio, listOf(track, Color.Transparent)) to ratio
            }
            everyGround(palette).forEach { (place, ground) ->
                // The bar on its track, and the legend swatch on the bare card.
                listOf("$place track" to drawnOver(track, ground), "$place legend" to ground).forEach { (spot, under) ->
                    val measured = drawn.map { (step, ratio) ->
                        val actual = measuredContrast(step, under)
                        assertTrue(
                            "[$theme] step ${step.hex()} on $spot reads ${"%.2f".format(actual)}, wants $ratio",
                            actual >= ratio,
                        )
                        actual
                    }
                    measured.zipWithNext().forEach { (lighter, heavier) ->
                        assertTrue(
                            "[$theme] steps on $spot read ${"%.2f".format(lighter)} then ${"%.2f".format(heavier)}",
                            heavier >= lighter * MIN_STEP_SEPARATION,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun feelsLikeLineReadsOverEveryGraphFill() {
        // The fill under the line runs from nothing up to its strongest, the
        // top of its gradient; the other fills are there or not.
        val fillSteps = (0..4).map { TEMPERATURE_FILL_ALPHA * it / 4f }
        listOf(NimbusPalette.Light, NimbusPalette.Dark).forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            val line = palette.markTint(
                ScaleColors.Orange500,
                wash = temperatureGraphFills(palette.accent, palette.overlayInk, palette.rainTint),
            )
            val layers = listOf(
                "rain bars" to palette.rainTint.copy(alpha = PRECIP_BAR_ALPHA),
                "normal band" to palette.overlayInk.copy(alpha = NORMAL_BAND_ALPHA),
                "confidence band" to palette.accent.copy(alpha = CONFIDENCE_FILL_ALPHA),
            )
            everyGround(palette).forEach { (place, ground) ->
                for (mask in 0 until (1 shl layers.size)) {
                    val present = layers.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
                    val beneath = present.fold(ground) { under, (_, layer) -> drawnOver(layer, under) }
                    fillSteps.forEach { fill ->
                        // Each layer is drawn, and rounded, in turn, as on screen.
                        val under = drawnOver(palette.accent.copy(alpha = fill), beneath)
                        val ratio = measuredContrast(drawnOver(line, under), under)
                        assertTrue(
                            "[$theme] feels-like ${line.hex()} on $place under ${present.map { it.first }} " +
                                "and a $fill fill reads ${"%.2f".format(ratio)}",
                            ratio >= WCAG_AA_NON_TEXT,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theHighlightedHourReadsOnItsAccentWash() {
        listOf(NimbusPalette.Light, NimbusPalette.Dark).forEach { palette ->
            val theme = if (palette.isLight) "light" else "dark"
            val wash = palette.accent.copy(alpha = 0.18f)
            val barWash = palette.overlayInk.copy(alpha = 0.1f).compositeOver(wash)
            val inks = colourSweep().map { Triple(it, palette.textTint(it, wash), palette.markTint(it, barWash, fade = 0.7f)) }
            val timeLabel = palette.textTint(palette.accent, wash)
            val rain = palette.textTint(palette.rainTint, wash)
            val snow = palette.textTint(palette.snowTint, wash)
            val pairs = everyGround(palette).flatMap { (place, ground) ->
                // The wash, then the bar track, each drawn and rounded in turn.
                val washed = drawnOver(wash, ground)
                val barTrack = drawnOver(palette.overlayInk.copy(alpha = 0.1f), washed)
                listOf(
                    ContrastPair("[$theme] minor text on highlighted $place", palette.textSecondary, washed, WCAG_AA_TEXT),
                    ContrastPair("[$theme] time label on highlighted $place", timeLabel, washed, WCAG_AA_TEXT),
                    ContrastPair("[$theme] rain text on highlighted $place", rain, washed, WCAG_AA_TEXT),
                    ContrastPair("[$theme] snow text on highlighted $place", snow, washed, WCAG_AA_TEXT),
                ) + inks.flatMap { (color, text, bar) ->
                    listOf(
                        ContrastPair("[$theme] text ${color.hex()} on highlighted $place", text, washed, WCAG_AA_TEXT),
                        ContrastPair(
                            "[$theme] bar ${color.hex()} on highlighted $place",
                            drawnOver(bar, barTrack),
                            barTrack,
                            WCAG_AA_NON_TEXT,
                        ),
                    )
                }
            }
            pairs.assertContrast()
        }
    }

    /** Every stop of every sky the app draws, for one theme. */
    private fun everySkyStop(palette: NimbusPalette): Set<Color> =
        listOf(0, 1, 2, 3, 45, 48, 51, 61, 71, 80, 95).flatMap { code ->
            listOf(true, false).flatMap { isDay -> palette.skyStops(isDay, code) }
        }.toSet()

    /** The card over every sky stop, the bare background and the alert banner's sheet. */
    private fun everyGround(palette: NimbusPalette): List<Pair<String, Color>> =
        everySkyStop(palette).map { "card over ${it.hex()}" to palette.cardBg.compositeOver(it) } +
            listOf("background" to palette.background, "alert banner" to palette.overlayCard)

    /** A translucent colour over [ground], rounded to 8 bits the way the screen draws it. */
    private fun drawnOver(color: Color, ground: Color): Color {
        val c = color.compositeOver(ground)
        return Color(
            red = (c.red * 255f).roundToInt(),
            green = (c.green * 255f).roundToInt(),
            blue = (c.blue * 255f).roundToInt(),
        )
    }

    private fun colourSweep(): List<Color> = buildList {
        for (hue in 0 until 360 step 15) {
            for (saturation in listOf(0f, 0.35f, 0.7f, 1f)) {
                for (step in 0..20) {
                    add(Color.hsl(hue.toFloat(), saturation, step / 20f))
                }
            }
        }
    }

    private fun Color.hex(): String = "#%08X".format(value.shr(32).toLong())

    private fun List<ContrastPair>.assertContrast() {
        forEach { pair ->
            val actual = measuredContrast(pair.foreground, pair.background)
            assertTrue(
                "${pair.name} contrast ${"%.2f".format(actual)} is below ${pair.minimumRatio}",
                actual >= pair.minimumRatio,
            )
        }
    }
}

private data class ContrastPair(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minimumRatio: Double,
)

/** The test's own WCAG ratio, kept apart from the one under test. */
private fun measuredContrast(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    fun channel(value: Float): Double {
        val sRgb = value.toDouble()
        return if (sRgb <= 0.03928) {
            sRgb / 12.92
        } else {
            ((sRgb + 0.055) / 1.055).pow(2.4)
        }
    }

    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

private const val WCAG_AA_TEXT = 4.5
private const val WCAG_AA_NON_TEXT = 3.0

/** A faint track only has to separate from its card, not carry text: 1.1:1 is visible, 1.0:1 is not. */
private const val MIN_WASH_SEPARATION = 1.1

/** Neighbouring Nowcast steps: each reads at least this much harder than the one before. */
private const val MIN_STEP_SEPARATION = 1.3
