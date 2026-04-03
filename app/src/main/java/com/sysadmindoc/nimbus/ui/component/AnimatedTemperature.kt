package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.util.WeatherFormatter
import com.sysadmindoc.nimbus.util.isReducedMotionEnabled
import kotlin.math.roundToInt

/**
 * Animated temperature display that smoothly rolls digits when the value changes.
 * Inspired by breezy-weather's NumberAnimTextView.
 * Falls back to static text when reduced motion is enabled.
 */
@Composable
fun AnimatedTemperature(
    temperatureCelsius: Double,
    settings: NimbusSettings,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = isReducedMotionEnabled()

    if (reducedMotion) {
        Text(
            text = WeatherFormatter.formatTemperature(temperatureCelsius, settings),
            style = MaterialTheme.typography.displayLarge,
            modifier = modifier,
        )
        return
    }

    val targetValue = remember(temperatureCelsius, settings.tempUnit) {
        WeatherFormatter.convertedTemp(temperatureCelsius, settings)
    }

    val animatable = remember { Animatable(targetValue.toFloat()) }

    LaunchedEffect(targetValue) {
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(durationMillis = 600),
        )
    }

    val displayValue = animatable.value.roundToInt()
    val symbol = settings.tempUnit.symbol

    Text(
        text = "$displayValue$symbol",
        style = MaterialTheme.typography.displayLarge,
        modifier = modifier,
    )
}
