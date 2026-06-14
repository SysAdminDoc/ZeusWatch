package com.sysadmindoc.nimbus.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.IconStyle
import com.sysadmindoc.nimbus.ui.theme.*
import com.sysadmindoc.nimbus.util.IconPackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CompositionLocal providing the Hilt-managed [IconPackManager] to weather icon
 * composables (mirrors the [LocalUnitSettings] pattern). Provided at the
 * MainActivity level so the CUSTOM icon style works app-wide without threading
 * the manager through every call site. Defaults to null (Material fallback).
 */
val LocalIconPackManager = staticCompositionLocalOf<IconPackManager?> { null }

/**
 * Maps WMO weather codes to icons.
 *
 * Supports three modes via [IconStyle]:
 * - **MATERIAL** / **METEOCONS**: Built-in Material Icons (Meteocons handled elsewhere via Lottie).
 * - **CUSTOM**: Loads bitmaps from a user-selected [IconPack] via [IconPackManager].
 *   Falls back to Material Icons when the custom pack cannot provide the requested icon.
 */
@Composable
fun WeatherIcon(
    weatherCode: WeatherCode,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconPackManager: IconPackManager? = null,
) {
    val settings = LocalUnitSettings.current
    val description = stringResource(weatherCode.descriptionRes())
    // Explicit param wins; otherwise fall back to the app-provided local.
    val packManager = iconPackManager ?: LocalIconPackManager.current

    // Try custom icon pack first
    if (settings.iconStyle == IconStyle.CUSTOM && packManager != null && settings.customIconPackId.isNotEmpty()) {
        val context = LocalContext.current
        val packId = settings.customIconPackId
        // Cheap synchronous cache peek keeps already-decoded icons flicker-free.
        val cached = remember(packId, weatherCode.code, isDay) {
            packManager.peekCachedIcon(packId, weatherCode.code, isDay)
        }
        // First decode per icon (disk read + BitmapFactory) runs off the main
        // thread so it can't jank the LazyColumn on first paint/scroll.
        val bitmap: Bitmap? by produceState(initialValue = cached, packId, weatherCode.code, isDay) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    packManager.findPack(packId, context)
                        ?.let { packManager.loadIcon(context, it, weatherCode.code, isDay) }
                }
            }
        }
        val resolvedBitmap = bitmap
        if (resolvedBitmap != null) {
            Image(
                bitmap = resolvedBitmap.asImageBitmap(),
                contentDescription = description,
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
            return
        }
        // Fall through to Material Icons if custom pack failed
    }

    // Default: Material Icons
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
        contentDescription = description,
        modifier = modifier,
        tint = if (tint != Color.Unspecified) tint else defaultTint,
    )
}
