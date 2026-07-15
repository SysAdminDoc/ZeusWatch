package com.sysadmindoc.nimbus.ui.screen.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusSurfaceVariant
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun MultiSourceChartCard(
    locationName: String,
    forecasts: List<CompareOverlayForecast>,
    isLoading: Boolean,
    unavailable: Boolean,
    loadFailed: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    settings: NimbusSettings,
    modifier: Modifier = Modifier,
) {
    val chartSeries = remember(forecasts, settings) {
        buildMultiSourceChartSeries(forecasts, settings)
    }
    val summary = remember(forecasts) { buildCompareOverlaySummary(forecasts) }
    val toggleLabel = stringResource(R.string.compare_chart_overlay_title)
    val toggleState = stringResource(if (enabled) R.string.common_on else R.string.common_off)
    val chartDescription = summary?.let {
        stringResource(
            R.string.compare_chart_overlay_semantics,
            locationName,
            it.sourceNames.joinToString(),
            WeatherFormatter.formatTemperatureDelta(it.maxTemperatureSpreadCelsius, settings),
            it.maxPrecipitationSpreadPoints,
        )
    } ?: stringResource(R.string.compare_chart_overlay_unavailable)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(NimbusGlassTop.copy(alpha = 0.78f), NimbusGlassBottom),
                ),
            )
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.compare_chart_overlay_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = NimbusTextPrimary,
                )
                Text(
                    text = stringResource(R.string.compare_chart_overlay_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics {
                    contentDescription = toggleLabel
                    stateDescription = toggleState
                },
            )
        }

        if (enabled) {
            MultiSourceChartBody(
                chartSeries = chartSeries,
                isLoading = isLoading,
                unavailable = unavailable,
                loadFailed = loadFailed,
                onRetry = onRetry,
                summary = summary,
                chartDescription = chartDescription,
                settings = settings,
            )
        }
    }
}

@Composable
private fun MultiSourceChartBody(
    chartSeries: List<MultiSourceChartSeries>,
    isLoading: Boolean,
    unavailable: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    summary: CompareOverlaySummary?,
    chartDescription: String,
    settings: NimbusSettings,
) {
    when {
        isLoading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = NimbusBlueAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.compare_chart_overlay_loading),
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }

        loadFailed && chartSeries.size < 2 -> MultiSourceLoadFailedNotice(onRetry = onRetry)

        unavailable || chartSeries.size < 2 -> Text(
            text = stringResource(R.string.compare_chart_overlay_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NimbusSurfaceVariant.copy(alpha = 0.62f))
                .padding(14.dp),
        )

        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MultiSourceLegend(chartSeries = chartSeries, settings = settings)
            MultiSourceTemperatureCanvas(
                series = chartSeries,
                description = chartDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            )
            summary?.let { MultiSourceSpreadSummary(it, settings) }
        }
    }
}

@Composable
private fun MultiSourceLoadFailedNotice(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusSurfaceVariant.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.compare_overlay_error),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.compare_overlay_retry),
                style = MaterialTheme.typography.labelLarge,
                color = NimbusBlueAccent,
            )
        }
    }
}

@Composable
private fun MultiSourceLegend(
    chartSeries: List<MultiSourceChartSeries>,
    settings: NimbusSettings,
) {
    val colors = rememberChartColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chartSeries.forEachIndexed { index, series ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MultiSourceLegendSwatch(
                    color = colors[index % colors.size],
                    dashPatternDp = chartLineDashPatternDp(index),
                )
                Text(
                    text = series.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.compare_chart_overlay_now_temp,
                        WeatherFormatter.formatTemperature(series.currentTemperatureCelsius, settings),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MultiSourceLegendSwatch(
    color: Color,
    dashPatternDp: List<Float>?,
) {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 4.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            pathEffect = dashPatternDp?.toDashPathEffect(this),
        )
    }
}

@Composable
private fun MultiSourceTemperatureCanvas(
    series: List<MultiSourceChartSeries>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberChartColors()
    val layoutDirection = LocalLayoutDirection.current
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusCardBg.copy(alpha = 0.58f))
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(10.dp))
            .semantics { contentDescription = description }
            .padding(10.dp),
    ) {
        val chartLeft = 18.dp.toPx()
        val chartRight = size.width - 18.dp.toPx()
        val chartTop = 12.dp.toPx()
        val chartBottom = size.height - 16.dp.toPx()
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val allPoints = series.flatMap { it.points }
        val minTemp = floor(allPoints.minOf { it.displayTemperature }) - 1.0
        val maxTemp = ceil(allPoints.maxOf { it.displayTemperature }) + 1.0
        val tempSpan = (maxTemp - minTemp).coerceAtLeast(4.0)
        // Shared time domain across all series: series with different start
        // hours or lengths must not be stretched onto the same index grid.
        val domain = overlayTimeDomain(series) ?: return@Canvas
        val isRtl = layoutDirection == LayoutDirection.Rtl

        fun xFor(time: LocalDateTime): Float {
            val logicalX = chartLeft + chartWidth * overlayTimeFraction(time, domain.first, domain.second)
            return if (isRtl) size.width - logicalX else logicalX
        }

        repeat(4) { line ->
            val y = chartTop + chartHeight * line / 3f
            drawLine(
                color = NimbusTextTertiary.copy(alpha = 0.14f),
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val bars = buildOverlayPrecipBars(series)
        val maxPrecip = bars.maxOfOrNull { it.probability }?.takeIf { it > 0 }
        if (maxPrecip != null) {
            val barWidth = chartWidth / bars.size.coerceAtLeast(2) * 0.58f
            bars.forEach { bar ->
                val barHeight = chartHeight * (bar.probability / maxPrecip.toFloat()) * 0.24f
                val x = xFor(bar.time)
                drawRect(
                    color = NimbusRainBlue.copy(alpha = 0.18f),
                    topLeft = Offset(x - barWidth / 2f, chartBottom - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }

        series.forEachIndexed { index, item ->
            val color = colors[index % colors.size]
            val pathEffect = chartLineDashPatternDp(index)?.toDashPathEffect(this)
            val path = Path()
            item.points.forEachIndexed { pointIndex, point ->
                val x = xFor(point.time)
                val normalized = ((point.displayTemperature - minTemp) / tempSpan).toFloat()
                val y = chartBottom - chartHeight * normalized
                if (pointIndex == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = pathEffect,
                ),
            )
            item.points.lastOrNull()?.let { point ->
                val x = xFor(point.time)
                val normalized = ((point.displayTemperature - minTemp) / tempSpan).toFloat()
                val y = chartBottom - chartHeight * normalized
                drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

private fun List<Float>.toDashPathEffect(density: Density): PathEffect = with(density) {
    PathEffect.dashPathEffect(map { it.dp.toPx() }.toFloatArray(), 0f)
}

@Composable
private fun MultiSourceSpreadSummary(
    summary: CompareOverlaySummary,
    settings: NimbusSettings,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpreadChip(
            label = stringResource(
                R.string.compare_chart_overlay_temp_spread,
                WeatherFormatter.formatTemperatureDelta(summary.maxTemperatureSpreadCelsius, settings),
            ),
            modifier = Modifier.weight(1f),
        )
        SpreadChip(
            label = stringResource(
                R.string.compare_chart_overlay_rain_spread,
                summary.maxPrecipitationSpreadPoints,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpreadChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = NimbusTextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusSurfaceVariant.copy(alpha = 0.64f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun rememberChartColors(): List<Color> = remember {
    listOf(NimbusBlueAccent, NimbusWarning, NimbusSuccess)
}

internal data class MultiSourceChartSeries(
    val label: String,
    val points: List<MultiSourceChartPoint>,
    val currentTemperatureCelsius: Double,
)

internal data class MultiSourceChartPoint(
    val time: LocalDateTime,
    val displayTemperature: Double,
    val precipitationProbability: Int,
)

internal data class CompareOverlaySummary(
    val sourceNames: List<String>,
    val maxTemperatureSpreadCelsius: Double,
    val maxPrecipitationSpreadPoints: Int,
)

internal data class OverlayPrecipBar(
    val time: LocalDateTime,
    val probability: Int,
)

/**
 * Union time range across every series so all lines and bars share one x axis.
 * Returns null when there are no points at all.
 */
internal fun overlayTimeDomain(
    series: List<MultiSourceChartSeries>,
): Pair<LocalDateTime, LocalDateTime>? {
    val times = series.flatMap { item -> item.points.map { it.time } }
    val start = times.minOrNull() ?: return null
    val end = times.maxOrNull() ?: return null
    return start to end
}

/** Fractional x position of [time] inside the shared chart time domain. */
internal fun overlayTimeFraction(
    time: LocalDateTime,
    start: LocalDateTime,
    end: LocalDateTime,
): Float {
    val totalMinutes = ChronoUnit.MINUTES.between(start, end).coerceAtLeast(1L)
    val offsetMinutes = ChronoUnit.MINUTES.between(start, time)
    return (offsetMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
}

/**
 * Timestamp-aligned precipitation bars: one bar per distinct hour holding the
 * maximum probability reported by any source for that hour.
 */
internal fun buildOverlayPrecipBars(series: List<MultiSourceChartSeries>): List<OverlayPrecipBar> =
    series
        .flatMap { it.points }
        .groupBy { it.time }
        .map { (time, points) -> OverlayPrecipBar(time, points.maxOf { it.precipitationProbability }) }
        .sortedBy { it.time }

/**
 * Non-color series differentiation: solid / dashed / dotted line patterns
 * (dp interval pairs), mirrored in the legend swatches so the sources stay
 * distinguishable when the palette hues collapse for color-blind users.
 */
internal fun chartLineDashPatternDp(index: Int): List<Float>? = when (index % 3) {
    1 -> listOf(6f, 4f)
    2 -> listOf(1.5f, 4.5f)
    else -> null
}

internal fun buildMultiSourceChartSeries(
    forecasts: List<CompareOverlayForecast>,
    settings: NimbusSettings,
): List<MultiSourceChartSeries> = forecasts.mapNotNull { forecast ->
    val points = forecast.weather.hourly
        .sortedBy { it.time }
        .take(24)
        .map {
            MultiSourceChartPoint(
                time = it.time.truncatedTo(ChronoUnit.HOURS),
                displayTemperature = WeatherFormatter.convertedTemp(it.temperature, settings),
                precipitationProbability = it.precipitationProbability,
            )
        }
    if (points.size < 2) {
        null
    } else {
        MultiSourceChartSeries(
            label = forecast.label,
            points = points,
            currentTemperatureCelsius = forecast.weather.current.temperature,
        )
    }
}

internal fun buildCompareOverlaySummary(forecasts: List<CompareOverlayForecast>): CompareOverlaySummary? {
    val hourlyBySource = forecasts
        .mapNotNull { forecast ->
            val hourly = forecast.weather.hourly
                .sortedBy { it.time }
                .take(24)
                .associateBy { it.time.truncatedTo(ChronoUnit.HOURS) }
            if (hourly.size < 2) null else forecast.label to hourly
        }
    if (hourlyBySource.size < 2) return null

    val hours = hourlyBySource
        .flatMap { it.second.keys }
        .distinct()
        .sorted()
    var maxTemperatureSpread = 0.0
    var maxPrecipitationSpread = 0
    hours.forEach { hour ->
        val values = hourlyBySource.mapNotNull { it.second[hour] }
        if (values.size >= 2) {
            maxTemperatureSpread = maxTemperatureSpread.coerceAtLeast(values.temperatureSpread())
            maxPrecipitationSpread = maxPrecipitationSpread.coerceAtLeast(values.precipitationSpread())
        }
    }

    return CompareOverlaySummary(
        sourceNames = hourlyBySource.map { it.first },
        maxTemperatureSpreadCelsius = maxTemperatureSpread,
        maxPrecipitationSpreadPoints = maxPrecipitationSpread,
    )
}

private fun List<HourlyConditions>.temperatureSpread(): Double =
    maxOf { it.temperature } - minOf { it.temperature }

private fun List<HourlyConditions>.precipitationSpread(): Int =
    maxOf { it.precipitationProbability } - minOf { it.precipitationProbability }
