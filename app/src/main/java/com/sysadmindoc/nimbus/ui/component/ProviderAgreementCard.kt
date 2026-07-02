package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.ProviderAgreementData
import com.sysadmindoc.nimbus.data.repository.ProviderAgreementLevel
import com.sysadmindoc.nimbus.data.repository.ProviderAgreementSnapshot
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.WeatherFormatter

@Composable
fun ProviderAgreementCard(
    data: ProviderAgreementData,
    modifier: Modifier = Modifier,
) {
    val settings = LocalUnitSettings.current
    val badge = stringResource(data.agreement.labelRes)
    val tempSpread = WeatherFormatter.formatTemperatureDelta(data.temperatureSpreadC, settings)
    val precipSpread = WeatherFormatter.formatPrecipitation(data.precipitationSpreadMm, settings)
    val semantics = stringResource(
        R.string.provider_agreement_semantics,
        badge,
        data.providers.size,
        tempSpread,
        precipSpread,
    )

    WeatherCard(
        titleRes = R.string.card_type_provider_agreement,
        statusLabel = badge,
        statusTint = data.agreement.tint,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semantics
        },
    ) {
        Text(
            text = stringResource(R.string.provider_agreement_window, data.providers.size),
            style = MaterialTheme.typography.labelMedium,
            color = NimbusTextSecondary,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ProviderAgreementMetric(
                label = stringResource(R.string.provider_agreement_temp_spread),
                value = tempSpread,
                modifier = Modifier.weight(1f),
                tint = data.agreement.tint,
            )
            ProviderAgreementMetric(
                label = stringResource(R.string.provider_agreement_precip_spread),
                value = precipSpread,
                modifier = Modifier.weight(1f),
                tint = data.agreement.tint,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.providers.forEach { provider ->
                ProviderAgreementProviderRow(
                    provider = provider,
                    settings = settings,
                )
            }
        }
    }
}

@Composable
private fun ProviderAgreementMetric(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusTextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
        )
    }
}

@Composable
private fun ProviderAgreementProviderRow(
    provider: ProviderAgreementSnapshot,
    settings: NimbusSettings,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = provider.displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = NimbusTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.provider_agreement_provider_values,
                WeatherFormatter.formatTemperature(provider.averageTemperatureC, settings),
                WeatherFormatter.formatPrecipitation(provider.precipitationTotalMm, settings),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = NimbusTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ProviderAgreementLevel.labelRes: Int
    get() = when (this) {
        ProviderAgreementLevel.STRONG -> R.string.provider_agreement_badge_strong
        ProviderAgreementLevel.MODERATE -> R.string.provider_agreement_badge_moderate
        ProviderAgreementLevel.DIVERGENT -> R.string.provider_agreement_badge_divergent
    }

private val ProviderAgreementLevel.tint: Color
    get() = when (this) {
        ProviderAgreementLevel.STRONG -> NimbusSuccess
        ProviderAgreementLevel.MODERATE -> NimbusBlueAccent
        ProviderAgreementLevel.DIVERGENT -> NimbusWarning
    }
