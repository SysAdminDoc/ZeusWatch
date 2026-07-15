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
        val cardBackground = NimbusCardBg.compositeOver(NimbusNavyDark)
        val pairs = listOf(
            ContrastPair("primary on navy", NimbusTextPrimary, NimbusNavyDark, WCAG_AA_TEXT),
            ContrastPair("secondary on navy", NimbusTextSecondary, NimbusNavyDark, WCAG_AA_TEXT),
            ContrastPair("tertiary on navy", NimbusTextTertiary, NimbusNavyDark, WCAG_AA_TEXT),
            ContrastPair("primary on surface", NimbusTextPrimary, NimbusSurface, WCAG_AA_TEXT),
            ContrastPair("secondary on surface", NimbusTextSecondary, NimbusSurface, WCAG_AA_TEXT),
            ContrastPair("tertiary on surface", NimbusTextTertiary, NimbusSurface, WCAG_AA_TEXT),
            ContrastPair("primary on card", NimbusTextPrimary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("secondary on card", NimbusTextSecondary, cardBackground, WCAG_AA_TEXT),
            ContrastPair("tertiary on card", NimbusTextTertiary, cardBackground, WCAG_AA_TEXT),
        )

        pairs.assertContrast()
    }

    @Test
    fun statusAndAccentTokensMeetNonTextContrastOnPrimarySurfaces() {
        val cardBackground = NimbusCardBg.compositeOver(NimbusNavyDark)
        val pairs = listOf(
            ContrastPair("accent on navy", NimbusBlueAccent, NimbusNavyDark, WCAG_AA_NON_TEXT),
            ContrastPair("warning on navy", NimbusWarning, NimbusNavyDark, WCAG_AA_NON_TEXT),
            ContrastPair("success on navy", NimbusSuccess, NimbusNavyDark, WCAG_AA_NON_TEXT),
            ContrastPair("error on navy", NimbusError, NimbusNavyDark, WCAG_AA_NON_TEXT),
            ContrastPair("accent on card", NimbusBlueAccent, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("warning on card", NimbusWarning, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("success on card", NimbusSuccess, cardBackground, WCAG_AA_NON_TEXT),
            ContrastPair("error on card", NimbusError, cardBackground, WCAG_AA_NON_TEXT),
        )

        pairs.assertContrast()
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
            // Adaptive tertiary is translucent, so judge it as rendered over the
            // app background — same as the card-background cases above.
            val tertiaryBackground = scheme.tertiary.compositeOver(NimbusNavyDark)
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
