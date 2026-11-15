package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings

/** How a metric value is tinted on surfaces that support semantic styles. */
internal enum class MetricTone { UNSPECIFIED, SAFE, CAUTION, DANGER }

internal data class WeatherMetric(
    val label: String,
    val value: String,
    val tone: MetricTone,
)

internal data class WeatherMetricLabels(
    val feelsLike: String,
    val uvIndex: String,
    val humidity: String,
    val wind: String,
)

/**
 * The three live metrics for the Android 17 `MetricStyle` weather notification:
 * feels-like, UV in daylight (humidity after dark, where UV is always 0), and wind.
 * Values use the same formatters as the rest of the app so unit settings apply.
 */
internal fun weatherNotificationMetrics(
    current: CurrentConditions,
    settings: NimbusSettings,
    labels: WeatherMetricLabels,
): List<WeatherMetric> {
    val middle = if (current.isDay) {
        WeatherMetric(labels.uvIndex, WeatherFormatter.formatUvIndex(current.uvIndex), uvTone(current.uvIndex))
    } else {
        WeatherMetric(labels.humidity, WeatherFormatter.formatHumidity(current.humidity), MetricTone.UNSPECIFIED)
    }
    return listOf(
        WeatherMetric(
            labels.feelsLike,
            WeatherFormatter.formatTemperature(current.feelsLike, settings),
            MetricTone.UNSPECIFIED,
        ),
        middle,
        WeatherMetric(
            labels.wind,
            WeatherFormatter.formatWindSpeed(current.windSpeed, current.windDirection, settings),
            MetricTone.UNSPECIFIED,
        ),
    )
}

/**
 * UV bands match the UV card: low (under 3) is safe, moderate and high (3 to under 8)
 * are caution, very high and extreme (8 and up) are danger.
 */
internal fun uvTone(uvIndex: Double): MetricTone = when {
    uvIndex < 3 -> MetricTone.SAFE
    uvIndex < 8 -> MetricTone.CAUTION
    else -> MetricTone.DANGER
}
