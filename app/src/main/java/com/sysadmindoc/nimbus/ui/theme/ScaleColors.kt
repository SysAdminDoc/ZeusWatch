package com.sysadmindoc.nimbus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Material swatches the cards' scales are built from: UV, AQI, pollen, Kp,
 * CAPE, comfort, flood and score steps, and the alert, health, driving and pet
 * severity levels. Each keeps one value in both themes because
 * its place on a scale is the meaning. Drawn on a card it goes through
 * [textTint] (text) or [markTint] (bars, arcs, dots, icons), which keep the
 * hue and fix the lightness for the theme.
 */
object ScaleColors {
    val Green500 = Color(0xFF4CAF50)
    val Green300 = Color(0xFF81C784)
    val LightGreen500 = Color(0xFF8BC34A)
    val Yellow500 = Color(0xFFFFEB3B)
    val Amber300 = Color(0xFFFFD54F)
    val Amber500 = Color(0xFFFFC107)
    val Amber800 = Color(0xFFFF8F00)
    val Orange200 = Color(0xFFFFCC80)
    val Orange300 = Color(0xFFFFB74D)
    val Orange500 = Color(0xFFFF9800)
    val DeepOrange300 = Color(0xFFFF8A65)
    val DeepOrange500 = Color(0xFFFF5722)
    val Red400 = Color(0xFFEF5350)
    val Red500 = Color(0xFFF44336)
    val Red700 = Color(0xFFD32F2F)
    val Pink500 = Color(0xFFE91E63)
    val Pink900 = Color(0xFF880E4F)
    val Purple500 = Color(0xFF9C27B0)
    val Purple800 = Color(0xFF6A1B9A)
    val Blue300 = Color(0xFF64B5F6)
    val Blue400 = Color(0xFF42A5F5)
    val Blue500 = Color(0xFF2196F3)
    val LightBlue300 = Color(0xFF4FC3F7)
    val Cyan200 = Color(0xFF80DEEA)
    val Grey500 = Color(0xFF9E9E9E)

    /** Daily range bar stops: cool, mild, warm. */
    val RangeCool = Color(0xFF73C5FF)
    val RangeMild = Color(0xFF7AB8FF)

    /**
     * Nowcast precipitation intensity, light to heavy. Only the hue is kept:
     * the card places each step by its contrast on the track, through
     * [NimbusPalette.stepTint].
     */
    val NowcastLight = Color(0xFF5B8DEF)
    val NowcastModerate = Color(0xFF3A7BD5)
    val NowcastHeavy = Color(0xFF1B4F9E)

    /** The Nowcast bar's dark-theme track, where no rain falls. */
    val NowcastTrack = Color(0xFF2A2A3A)
}
