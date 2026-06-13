package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

private val GHI_COLOR = Color(0xFFFFD54F)
private val DNI_COLOR = Color(0xFFFF8F00)

@Composable
fun SolarIrradianceCard(
    hourly: List<HourlyConditions>,
    modifier: Modifier = Modifier,
) {
    val ghiData = hourly.take(24).mapNotNull { it.shortwaveRadiation }
    if (ghiData.isEmpty()) return

    val dniData = hourly.take(24).mapNotNull { it.directNormalIrradiance }
    val currentGhi = ghiData.firstOrNull() ?: 0.0
    val currentDni = dniData.firstOrNull() ?: 0.0
    val peakGhi = ghiData.maxOrNull() ?: 0.0
    val dailyEnergy = ghiData.sum() / 1000.0

    val desc = stringResource(
        R.string.solar_semantics,
        currentGhi.toInt(),
        currentDni.toInt(),
        String.format("%.1f", dailyEnergy),
    )

    WeatherCard(
        titleRes = R.string.card_title_solar,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = desc
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.solar_ghi_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.solar_wm2_value, currentGhi.toInt()),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = GHI_COLOR,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.solar_dni_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.solar_wm2_value, currentDni.toInt()),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = DNI_COLOR,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = stringResource(R.string.solar_daily_estimate),
                    style = MaterialTheme.typography.labelSmall,
                    color = NimbusTextTertiary,
                )
                Text(
                    text = stringResource(R.string.solar_kwh_value, String.format("%.1f", dailyEnergy)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SolarChart(
            ghiValues = ghiData,
            dniValues = dniData,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
        )
    }
}

@Composable
private fun SolarChart(
    ghiValues: List<Double>,
    dniValues: List<Double>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (ghiValues.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val maxVal = (ghiValues + dniValues).maxOrNull()?.coerceAtLeast(100.0) ?: 100.0
        val strokeW = 2.dp.toPx()

        fun drawCurve(values: List<Double>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * w / (values.size - 1).coerceAtLeast(1)
                val y = h - (v / maxVal * h).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        }

        drawCurve(ghiValues, GHI_COLOR)
        drawCurve(dniValues, DNI_COLOR)

        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
