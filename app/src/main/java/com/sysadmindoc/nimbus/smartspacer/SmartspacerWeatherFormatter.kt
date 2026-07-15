package com.sysadmindoc.nimbus.smartspacer

import android.content.Context
import androidx.annotation.DrawableRes
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

internal data class SmartspacerWeatherSnapshot(
    val targetTitle: String,
    val targetSubtitle: String,
    val complicationText: String,
    val condition: String,
    val temperature: Int,
    val useCelsius: Boolean,
    val nextHourPrecipitationProbability: Int?,
    @param:DrawableRes val iconRes: Int,
    val stateIcon: SmartspacerWeatherStateIcon,
)

internal enum class SmartspacerWeatherStateIcon {
    UNKNOWN_ICON,
    SUNNY,
    CLEAR_NIGHT,
    MOSTLY_SUNNY,
    MOSTLY_CLEAR_NIGHT,
    PARTLY_CLOUDY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    HAZE_FOG_DUST_SMOKE,
    DRIZZLE,
    HEAVY_RAIN,
    SHOWERS_RAIN,
    SCATTERED_SHOWERS_DAY,
    SCATTERED_SHOWERS_NIGHT,
    STRONG_TSTORMS,
    BLIZZARD,
    HEAVY_SNOW,
    SCATTERED_SNOW_SHOWERS_DAY,
    SCATTERED_SNOW_SHOWERS_NIGHT,
    WINTRY_MIX_RAIN_SNOW,
}

/**
 * User-facing snapshot strings, injected so the pure JVM formatter tests don't
 * need Android resources. [from] resolves them from the localized resources.
 */
internal class SmartspacerWeatherStrings(
    val nextHourRain: (Int) -> String,
    val nextHourRainUnavailable: String,
    val currentLocation: String,
) {
    companion object {
        fun from(context: Context): SmartspacerWeatherStrings = SmartspacerWeatherStrings(
            nextHourRain = { percent -> context.getString(R.string.smartspacer_next_hour_rain, percent) },
            nextHourRainUnavailable = context.getString(R.string.smartspacer_next_hour_rain_unavailable),
            currentLocation = context.getString(R.string.smartspacer_current_location),
        )
    }
}

internal fun buildSmartspacerWeatherSnapshot(
    weather: WeatherData,
    tempUnit: TempUnit,
    strings: SmartspacerWeatherStrings,
    referenceInstant: Instant = Instant.now(),
    fallbackZone: ZoneId = ZoneId.systemDefault(),
): SmartspacerWeatherSnapshot {
    val settings = NimbusSettings(tempUnit = tempUnit)
    val temperature = WeatherFormatter.convertedTemp(weather.current.temperature, settings).roundToInt()
    val temperatureText = "${temperature}\u00B0"
    val condition = weather.current.sourceConditionText
        ?.takeIf { it.isNotBlank() }
        ?: weather.current.weatherCode.description
    // Hourly timestamps are location-local, so the "next hour" anchor must be
    // computed in the location's zone — a device-local LocalDateTime.now()
    // picks the wrong hour for any saved city in another timezone.
    val zone = weather.location.timeZone
        ?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() }
        ?: fallbackZone
    val referenceTime = LocalDateTime.ofInstant(referenceInstant, zone)
    val nextHourPrecipitation = weather.hourly
        .firstOrNull { !it.time.isBefore(referenceTime) }
        ?: weather.hourly.firstOrNull()
    val nextHourRain = nextHourPrecipitation?.precipitationProbability
    val nextHourText = nextHourRain?.let(strings.nextHourRain) ?: strings.nextHourRainUnavailable
    val locationName = weather.location.name.takeIf { it.isNotBlank() } ?: strings.currentLocation
    val icon = weather.current.weatherCode.toSmartspacerIcon(weather.current.isDay)

    return SmartspacerWeatherSnapshot(
        targetTitle = "$locationName $temperatureText",
        targetSubtitle = "$condition. $nextHourText.",
        complicationText = temperatureText,
        condition = condition,
        temperature = temperature,
        useCelsius = tempUnit == TempUnit.CELSIUS,
        nextHourPrecipitationProbability = nextHourRain,
        iconRes = icon.iconRes,
        stateIcon = icon.stateIcon,
    )
}

private data class SmartspacerWeatherIcon(
    @param:DrawableRes val iconRes: Int,
    val stateIcon: SmartspacerWeatherStateIcon,
)

private fun WeatherCode.toSmartspacerIcon(isDay: Boolean): SmartspacerWeatherIcon =
    when {
        this == WeatherCode.CLEAR_SKY && isDay -> SmartspacerWeatherIcon(
            R.drawable.ic_w_sunny,
            SmartspacerWeatherStateIcon.SUNNY,
        )
        this == WeatherCode.CLEAR_SKY -> SmartspacerWeatherIcon(
            R.drawable.ic_w_night,
            SmartspacerWeatherStateIcon.CLEAR_NIGHT,
        )
        this == WeatherCode.MAINLY_CLEAR && isDay -> SmartspacerWeatherIcon(
            R.drawable.ic_w_sunny,
            SmartspacerWeatherStateIcon.MOSTLY_SUNNY,
        )
        this == WeatherCode.MAINLY_CLEAR -> SmartspacerWeatherIcon(
            R.drawable.ic_w_night,
            SmartspacerWeatherStateIcon.MOSTLY_CLEAR_NIGHT,
        )
        this == WeatherCode.PARTLY_CLOUDY && isDay -> SmartspacerWeatherIcon(
            R.drawable.ic_w_partly_cloudy,
            SmartspacerWeatherStateIcon.PARTLY_CLOUDY,
        )
        this == WeatherCode.PARTLY_CLOUDY -> SmartspacerWeatherIcon(
            R.drawable.ic_w_partly_cloudy,
            SmartspacerWeatherStateIcon.PARTLY_CLOUDY_NIGHT,
        )
        isCloudy -> SmartspacerWeatherIcon(R.drawable.ic_w_cloudy, SmartspacerWeatherStateIcon.CLOUDY)
        isFoggy -> SmartspacerWeatherIcon(R.drawable.ic_w_fog, SmartspacerWeatherStateIcon.HAZE_FOG_DUST_SMOKE)
        code in 51..57 -> SmartspacerWeatherIcon(R.drawable.ic_w_rain, SmartspacerWeatherStateIcon.DRIZZLE)
        code in 61..67 -> SmartspacerWeatherIcon(R.drawable.ic_w_rain, SmartspacerWeatherStateIcon.HEAVY_RAIN)
        code in 80..82 && isDay -> SmartspacerWeatherIcon(
            R.drawable.ic_w_rain,
            SmartspacerWeatherStateIcon.SCATTERED_SHOWERS_DAY,
        )
        code in 80..82 -> SmartspacerWeatherIcon(
            R.drawable.ic_w_rain,
            SmartspacerWeatherStateIcon.SCATTERED_SHOWERS_NIGHT,
        )
        isSnowy && isDay -> SmartspacerWeatherIcon(
            R.drawable.ic_w_snow,
            SmartspacerWeatherStateIcon.SCATTERED_SNOW_SHOWERS_DAY,
        )
        isSnowy -> SmartspacerWeatherIcon(
            R.drawable.ic_w_snow,
            SmartspacerWeatherStateIcon.SCATTERED_SNOW_SHOWERS_NIGHT,
        )
        isStormy -> SmartspacerWeatherIcon(R.drawable.ic_w_thunderstorm, SmartspacerWeatherStateIcon.STRONG_TSTORMS)
        else -> SmartspacerWeatherIcon(R.drawable.ic_w_partly_cloudy, SmartspacerWeatherStateIcon.UNKNOWN_ICON)
    }
