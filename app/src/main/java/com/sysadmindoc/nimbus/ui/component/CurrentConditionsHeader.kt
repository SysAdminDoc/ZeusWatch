package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusHeroGlow
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CurrentConditionsHeader(
    current: CurrentConditions,
    locationName: String,
    modifier: Modifier = Modifier,
    yesterdayHigh: Double? = null,
) {
    val s = LocalUnitSettings.current
    val shape = RoundedCornerShape(34.dp)
    val feelsLikeReason = WeatherFormatter.feelsLikeReason(
        current.temperature, current.feelsLike, current.windSpeed, current.humidity,
    )
    val feelsLikeText = buildString {
        append("Feels like ${WeatherFormatter.formatTemperature(current.feelsLike, s)}")
        if (feelsLikeReason != null) append(" • $feelsLikeReason")
    }
    val comparisonLabel = yesterdayHigh?.let {
        val todayConverted = WeatherFormatter.convertedTemp(current.dailyHigh, s)
        val yesterdayConverted = WeatherFormatter.convertedTemp(it, s)
        val diff = (todayConverted - yesterdayConverted).roundToInt()
        if (abs(diff) >= 2) {
            if (diff > 0) "${diff}° warmer" else "${abs(diff)}° cooler"
        } else {
            null
        }
    }
    val comparisonColor = yesterdayHigh?.let {
        val todayConverted = WeatherFormatter.convertedTemp(current.dailyHigh, s)
        val yesterdayConverted = WeatherFormatter.convertedTemp(it, s)
        if (todayConverted - yesterdayConverted >= 0) NimbusWarning else NimbusRainBlue
    } ?: NimbusTextSecondary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop,
                        Color.White.copy(alpha = 0.03f),
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, shape)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroPill(
                text = locationName,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = NimbusBlueAccent,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
            HeroPill(text = if (current.isDay) "DAYLIGHT" else "OVERNIGHT")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                AnimatedTemperature(
                    temperatureCelsius = current.temperature,
                    settings = s,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = current.weatherCode.description,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.typography.titleLarge.color.copy(alpha = 0.92f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = feelsLikeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NimbusTextSecondary,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NimbusHeroGlow,
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .border(1.dp, NimbusCardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedWeatherIcon(
                    weatherCode = current.weatherCode,
                    isDay = current.isDay,
                    iconStyle = s.iconStyle,
                    modifier = Modifier.size(54.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        )
        {
            HeroMetricChip(
                modifier = Modifier.weight(1f),
                label = "High",
                value = WeatherFormatter.formatTemperature(current.dailyHigh, s),
            )
            HeroMetricChip(
                modifier = Modifier.weight(1f),
                label = "Low",
                value = WeatherFormatter.formatTemperature(current.dailyLow, s),
            )
            comparisonLabel?.let {
                HeroMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Trend",
                    value = it,
                    accentColor = comparisonColor,
                )
            }
        }
    }
}

@Composable
private fun HeroPill(
    text: String,
    icon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        if (icon != null) {
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeroMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextSecondary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor,
        )
    }
}
