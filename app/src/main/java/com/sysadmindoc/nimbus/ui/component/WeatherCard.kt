package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary

@Composable
fun WeatherCard(
    modifier: Modifier = Modifier,
    @StringRes titleRes: Int,
    statusLabel: String? = null,
    statusTint: Color = NimbusTextSecondary,
    content: @Composable ColumnScope.() -> Unit,
) {
    WeatherCard(
        modifier = modifier,
        title = stringResource(titleRes),
        statusLabel = statusLabel,
        statusTint = statusTint,
        content = content,
    )
}

@Composable
fun WeatherCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    statusLabel: String? = null,
    statusTint: Color = NimbusTextSecondary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .animateContentSize(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
            .background(NimbusCardBg)
            .border(
                width = 1.dp,
                color = NimbusCardBorder,
                shape = shape,
            )
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp,
                            ),
                            color = NimbusBlueAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!statusLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        NimbusStatusBadge(
                            text = statusLabel,
                            tint = statusTint,
                            modifier = Modifier.widthIn(max = 156.dp),
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 16.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NimbusCardBorder.copy(alpha = 0.72f)),
                )
            }
            content()
        }
    }
}
