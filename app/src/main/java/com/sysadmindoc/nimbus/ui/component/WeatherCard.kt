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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassHighlight
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusHeroGlowSoft
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
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .animateContentSize(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.86f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassHighlight.copy(alpha = 0.9f),
                        NimbusCardBorder,
                        Color.White.copy(alpha = 0.05f),
                    ),
                ),
                shape = shape,
            )
            .semantics(mergeDescendants = true) {}
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NimbusHeroGlowSoft,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
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
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            NimbusBlueAccent.copy(alpha = 0.94f),
                                            NimbusBlueAccent.copy(alpha = 0.38f),
                                        ),
                                    ),
                                ),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp,
                            ),
                            color = NimbusTextSecondary,
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
                        .padding(top = 12.dp, bottom = 18.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NimbusBlueAccent.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            content()
        }
    }
}
