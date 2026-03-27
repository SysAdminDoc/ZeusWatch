package com.sysadmindoc.nimbus.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.IconStyle
import com.sysadmindoc.nimbus.util.MeteoconMapper

/**
 * Animated weather icon that switches between Material Icons and Meteocons Lottie
 * based on the user's iconStyle preference.
 *
 * Falls back to Material Icons if the Lottie asset fails to load
 * (e.g., assets not yet downloaded).
 */
@Composable
fun AnimatedWeatherIcon(
    weatherCode: WeatherCode,
    isDay: Boolean,
    iconStyle: IconStyle,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    when (iconStyle) {
        IconStyle.MATERIAL -> {
            WeatherIcon(
                weatherCode = weatherCode,
                isDay = isDay,
                modifier = modifier,
                tint = tint,
            )
        }
        IconStyle.METEOCONS -> {
            val assetPath = MeteoconMapper.getLottieAsset(weatherCode, isDay)
            val composition by rememberLottieComposition(
                LottieCompositionSpec.Asset(assetPath)
            )
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = modifier,
                )
            } else {
                // Fallback to Material Icons when Lottie asset not available
                WeatherIcon(
                    weatherCode = weatherCode,
                    isDay = isDay,
                    modifier = modifier,
                    tint = tint,
                )
            }
        }
        IconStyle.CUSTOM -> {
            // Custom icon packs handled in WeatherIcon via IconPackManager
            // Falls back to Material Icons if pack can't load the icon
            WeatherIcon(
                weatherCode = weatherCode,
                isDay = isDay,
                modifier = modifier,
                tint = tint,
            )
        }
    }
}
