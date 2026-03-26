package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.sysadmindoc.nimbus.data.repository.CardType
import com.sysadmindoc.nimbus.data.repository.NimbusSettings

/**
 * Renders weather cards in the user's preferred order, respecting visibility toggles.
 * Each CardType maps to a composable via the [cardContent] lambda.
 *
 * Usage:
 * ```
 * ReorderableCardColumn(
 *     settings = settings,
 *     contentPadding = layout.contentPadding,
 *     cardSpacing = layout.cardSpacing,
 * ) { cardType ->
 *     when (cardType) {
 *         CardType.WEATHER_SUMMARY -> WeatherSummaryCard(...)
 *         CardType.RADAR_PREVIEW -> RadarPreviewCard(...)
 *         // ... etc
 *     }
 * }
 * ```
 */
@Composable
fun ReorderableCardColumn(
    settings: NimbusSettings,
    contentPadding: Dp,
    cardSpacing: Dp,
    modifier: Modifier = Modifier,
    cardContent: @Composable (CardType) -> Unit,
) {
    val enabledCards = settings.cardOrder.filter { card ->
        card.name !in settings.disabledCards
    }

    enabledCards.forEachIndexed { index, cardType ->
        cardContent(cardType)
        if (index < enabledCards.lastIndex) {
            Spacer(modifier = Modifier.height(cardSpacing))
        }
    }
}
