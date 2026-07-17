package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import kotlin.math.roundToInt

@Composable
internal fun VicoLineTrendChart(
    values: List<Double>,
    labels: List<String>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val chartValues = values.forDisplayDirection()
    LaunchedEffect(chartValues) {
        modelProducer.runTransaction {
            lineModel { series(y = chartValues) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(lineColor))),
                ),
            ),
            getXStep = { _, _, _ -> 1.0 },
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    )
    TrendTimeLabels(labels = labels)
}

@Composable
internal fun VicoColumnTrendChart(
    values: List<Double>,
    labels: List<String>,
    columnColor: Color,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val chartValues = values.forDisplayDirection()
    LaunchedEffect(chartValues) {
        modelProducer.runTransaction {
            columnModel { series(y = chartValues) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(columnColor), thickness = 8.dp),
                ),
            ),
            getXStep = { _, _, _ -> 1.0 },
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp),
    )
    TrendTimeLabels(labels = labels)
}

@Composable
internal fun VicoComboTrendChart(
    lineValues: List<Double>,
    columnValues: List<Double>,
    labels: List<String>,
    lineColor: Color,
    columnColor: Color,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val chartLineValues = lineValues.forDisplayDirection()
    val chartColumnValues = columnValues.forDisplayDirection()
    LaunchedEffect(chartLineValues, chartColumnValues) {
        modelProducer.runTransaction {
            columnModel { series(y = chartColumnValues) }
            lineModel { series(y = chartLineValues) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(columnColor), thickness = 6.dp),
                ),
            ),
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(lineColor))),
                ),
            ),
            getXStep = { _, _, _ -> 1.0 },
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp),
    )
    TrendTimeLabels(labels = labels)
}

/**
 * Picks [labelCount] evenly spaced indices into a series of [size] points so
 * that each label sits at the same fractional position as the data point it
 * describes. [TrendTimeLabels] lays labels out with SpaceBetween (0%, 33%,
 * 67%, 100% of the width for four labels), so the indices must be
 * round(i * (size - 1) / (labelCount - 1)) — e.g. 0, 8, 15, 23 for a
 * 24-point series — not 0, 6, 12, 18.
 */
internal fun trendLabelIndices(size: Int, labelCount: Int = 4): List<Int> {
    if (size <= 0 || labelCount <= 0) return emptyList()
    if (labelCount == 1 || size == 1) return listOf(0)
    return (0 until labelCount)
        .map { i -> (i * (size - 1).toFloat() / (labelCount - 1)).roundToInt() }
        .distinct()
}

@Composable
private fun List<Double>.forDisplayDirection(): List<Double> =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        asReversed()
    } else {
        this
    }

@Composable
private fun TrendTimeLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
