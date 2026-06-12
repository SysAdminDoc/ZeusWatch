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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

internal val WearBackground = Color(0xFF070B14)
internal val WearPanelTop = Color(0xFF121D32)
internal val WearPanelBottom = Color(0xFF08101F)
internal val WearPanelBorder = Color(0x446EA9E8)
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
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        WearPanelTop,
                        WearPanelBottom,
                    ),
                ),
                shape,
            )
            .border(1.dp, WearPanelBorder, shape)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .semantics {
                    contentDescription = title
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon.ifBlank { "!" },
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
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
                color = WearTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction, role = Role.Button)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun WearMiniBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = WearBlueAccent,
) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WearLinkButton(
            text = primary.first,
            onClick = primary.second,
            modifier = Modifier.weight(1f),
        )
        secondary?.let {
            WearLinkButton(
                text = it.first,
                onClick = it.second,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WearLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = WearBlueAccent,
        textAlign = TextAlign.Center,
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(WearBlueAccent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .border(1.dp, WearBlueAccent.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}
