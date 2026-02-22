package com.sysadmindoc.nimbus.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.ui.theme.*

/**
 * Maps WMO weather codes to Material Icons with condition-appropriate colors.
 * Phase 2+ will upgrade to animated Lottie icons.
 */
@Composable
fun WeatherIcon(
    weatherCode: WeatherCode,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val (icon, defaultTint) = when (weatherCode) {
        WeatherCode.CLEAR_SKY,
        WeatherCode.MAINLY_CLEAR -> {
            if (isDay) Icons.Filled.WbSunny to NimbusSunYellow
            else Icons.Outlined.NightsStay to NimbusMoonBlue
        }

        WeatherCode.PARTLY_CLOUDY -> {
            if (isDay) Icons.Filled.WbCloudy to NimbusTextSecondary
            else Icons.Outlined.Cloud to NimbusMoonBlue
        }

        WeatherCode.OVERCAST -> Icons.Filled.Cloud to NimbusTextTertiary

        WeatherCode.FOG,
        WeatherCode.DEPOSITING_RIME_FOG -> Icons.Filled.Air to NimbusFogGray

        WeatherCode.DRIZZLE_LIGHT,
        WeatherCode.DRIZZLE_MODERATE,
        WeatherCode.DRIZZLE_DENSE -> Icons.Filled.WaterDrop to NimbusRainBlue

        WeatherCode.FREEZING_DRIZZLE_LIGHT,
        WeatherCode.FREEZING_DRIZZLE_DENSE -> Icons.Outlined.Grain to NimbusRainBlue

        WeatherCode.RAIN_SLIGHT,
        WeatherCode.RAIN_MODERATE,
        WeatherCode.RAIN_HEAVY -> Icons.Filled.WaterDrop to NimbusRainBlue

        WeatherCode.FREEZING_RAIN_LIGHT,
        WeatherCode.FREEZING_RAIN_HEAVY -> Icons.Outlined.Grain to NimbusRainBlue

        WeatherCode.SNOW_SLIGHT,
        WeatherCode.SNOW_MODERATE,
        WeatherCode.SNOW_HEAVY,
        WeatherCode.SNOW_GRAINS -> Icons.Outlined.AcUnit to NimbusSnowWhite

        WeatherCode.RAIN_SHOWERS_SLIGHT,
        WeatherCode.RAIN_SHOWERS_MODERATE,
        WeatherCode.RAIN_SHOWERS_VIOLENT -> Icons.Filled.CloudQueue to NimbusRainBlue

        WeatherCode.SNOW_SHOWERS_SLIGHT,
        WeatherCode.SNOW_SHOWERS_HEAVY -> Icons.Outlined.AcUnit to NimbusSnowWhite

        WeatherCode.THUNDERSTORM,
        WeatherCode.THUNDERSTORM_HAIL_SLIGHT,
        WeatherCode.THUNDERSTORM_HAIL_HEAVY -> Icons.Filled.Thunderstorm to NimbusStormPurple

        WeatherCode.UNKNOWN -> Icons.Outlined.WbTwilight to NimbusTextTertiary
    }

    Icon(
        imageVector = icon,
        contentDescription = weatherCode.description,
        modifier = modifier,
        tint = if (tint != Color.Unspecified) tint else defaultTint,
    )
}
