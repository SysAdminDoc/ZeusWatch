package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

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
        )

        pairs.assertContrast()
    }

    @Test
    fun statusAndAccentTokensMeetNonTextContrastOnPrimarySurfaces() {
        val dark = NimbusPalette.Dark
        val cardBackground = dark.cardBg.compositeOver(dark.background)
        val pairs = listOf(
            ContrastPair("accent on navy", NimbusBlueAccent, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("warning on navy", dark.warning, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("success on navy", dark.success, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("error on navy", dark.error, dark.background, WCAG_AA_NON_TEXT),
            ContrastPair("accent on card", NimbusBlueAccent, cardBackground, WCAG_AA_NON_TEXT),
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

    private fun List<ContrastPair>.assertContrast() {
        forEach { pair ->
            val actual = contrastRatio(pair.foreground, pair.background)
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

private fun contrastRatio(foreground: Color, background: Color): Double {
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
