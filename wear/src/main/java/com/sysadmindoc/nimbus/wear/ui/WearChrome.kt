package com.sysadmindoc.nimbus.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

internal val WearBackground = Color(0xFF070B14)
internal val WearPanelTop = Color(0xFF111A2D)
internal val WearPanelBottom = Color(0xFF0B1222)
internal val WearPanelBorder = Color(0x334E6D9E)
internal val WearTextPrimary = Color(0xFFF4F7FC)
internal val WearTextSecondary = Color(0xFFBCC6DB)
internal val WearTextTertiary = Color(0xFF7D89A3)
internal val WearBlueAccent = Color(0xFF6EA9E8)
internal val WearAlertAccent = Color(0xFFFF8A65)
internal val WearSuccessAccent = Color(0xFF73D39C)

@Composable
internal fun WearPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        WearPanelTop,
                        WearPanelBottom,
                    ),
                ),
                RoundedCornerShape(20.dp),
            )
            .border(1.dp, WearPanelBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun WearHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = WearTextPrimary,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = WearTextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun WearStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: String = "\u2601\uFE0F",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accent: Color = WearBlueAccent,
) {
    WearPanel(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .background(accent.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = icon, fontSize = 18.sp)
        }
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = WearTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = message,
            fontSize = 11.sp,
            color = WearTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAction)
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun WearMiniPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = WearBlueAccent,
) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = accent,
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
internal fun WearLinkRow(
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = primary.first,
            fontSize = 10.sp,
            color = WearBlueAccent,
            modifier = Modifier.clickable(onClick = primary.second),
        )
        secondary?.let {
            Text(
                text = it.first,
                fontSize = 10.sp,
                color = WearBlueAccent,
                modifier = Modifier.clickable(onClick = it.second),
            )
        }
    }
}
