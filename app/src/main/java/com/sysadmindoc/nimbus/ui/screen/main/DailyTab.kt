package com.sysadmindoc.nimbus.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.ui.component.WeatherIcon
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.util.WeatherFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTab(
    daily: List<DailyConditions>,
    locationName: String,
    referenceDate: LocalDate? = daily.firstOrNull()?.date,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEEE") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    // No windowInsetsPadding here: the Scaffold innerPadding around this tab
    // already covers safeDrawing — applying it again doubled the top inset.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.forecast_daily_days_title, daily.size),
                style = MaterialTheme.typography.headlineLarge,
                color = NimbusTextPrimary,
            )
            Text(
                locationName,
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextSecondary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NimbusCardBg)
                    .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        itemsIndexed(daily, key = { _, day -> day.date }) { index, day ->
            DailyDetailRow(
                day = day,
                dayLabel = when {
                    referenceDate != null && day.date == referenceDate -> stringResource(R.string.common_today)
                    referenceDate != null && day.date == referenceDate.plusDays(1) -> stringResource(R.string.common_tomorrow)
                    else -> day.date.format(dayFormatter)
                },
                dateLabel = day.date.format(dateFormatter),
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
    }
}

@Composable
private fun DailyDetailRow(
    day: DailyConditions,
    dayLabel: String,
    dateLabel: String,
) {
    val s = com.sysadmindoc.nimbus.ui.component.LocalUnitSettings.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.52f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, shape)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(100.dp)) {
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextPrimary,
                )
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextTertiary,
                )
            }

            WeatherIcon(
                weatherCode = day.weatherCode,
                isDay = true,
                modifier = Modifier.size(36.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    day.weatherCode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextSecondary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(
                        R.string.forecast_high_abbrev,
                        WeatherFormatter.formatTemperature(day.temperatureHigh, s),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = NimbusTextPrimary,
                )
                Text(
                    stringResource(
                        R.string.forecast_low_abbrev,
                        WeatherFormatter.formatTemperature(day.temperatureLow, s),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = NimbusTextTertiary,
                )
            }
        }

        // Extra details row
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (day.precipitationProbability > 0) {
                DetailChip(stringResource(R.string.forecast_detail_rain), "${day.precipitationProbability}%")
            }
            day.windSpeedMax?.let {
                DetailChip(stringResource(R.string.forecast_detail_wind), WeatherFormatter.formatWindSpeed(it, s))
            }
            day.uvIndexMax?.let {
                DetailChip(stringResource(R.string.forecast_tab_uv), WeatherFormatter.formatUvLevel(it))
            }
        }
    }
}

@Composable
private fun DetailChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NimbusCardBg)
            .border(1.dp, NimbusCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = NimbusBlueAccent,
        )
    }
}
