package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

@Composable
fun GlassActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> NimbusCardBorder.copy(alpha = 0.48f)
            focused -> NimbusBlueAccent.copy(alpha = 0.72f)
            highlighted -> NimbusBlueAccent.copy(alpha = 0.52f)
            pressed -> NimbusBlueAccent.copy(alpha = 0.36f)
            else -> NimbusCardBorder
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "glassActionBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> NimbusTextTertiary.copy(alpha = 0.56f)
            highlighted || pressed || focused -> NimbusTextPrimary
            else -> NimbusTextPrimary.copy(alpha = 0.88f)
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "glassActionIcon",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = if (enabled && pressed) 0.97f else 1f
                scaleY = if (enabled && pressed) 0.97f else 1f
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (!enabled) {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.38f),
                            NimbusGlassBottom.copy(alpha = 0.66f),
                        )
                    } else if (highlighted || pressed || focused) {
                        listOf(
                            NimbusBlueAccent.copy(alpha = if (pressed) 0.24f else 0.18f),
                            NimbusGlassBottom,
                        )
                    } else {
                        listOf(
                            NimbusGlassTop.copy(alpha = 0.78f),
                            NimbusGlassBottom,
                        )
                    },
                ),
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
            .semantics {
                this.contentDescription = contentDescription
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = NimbusBlueAccent,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = NimbusTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun PremiumMessageCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color = NimbusBlueAccent,
    loading: Boolean = false,
    badgeText: String? = null,
    primaryActionLabel: String? = null,
    primaryActionIcon: ImageVector? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    secondaryActionIcon: ImageVector? = null,
    onSecondaryAction: (() -> Unit)? = null,
    tertiaryActionLabel: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.10f),
                        NimbusGlassTop.copy(alpha = 0.82f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.24f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = tint,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NimbusTextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        if (!badgeText.isNullOrBlank()) {
            NimbusStatusBadge(
                text = badgeText,
                tint = tint,
                emphasized = true,
            )
        }

        content?.invoke(this)

        if (primaryActionLabel != null && onPrimaryAction != null) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tint,
                    contentColor = NimbusTextPrimary,
                ),
            ) {
                if (primaryActionIcon != null) {
                    Icon(
                        imageVector = primaryActionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = primaryActionLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (secondaryActionLabel != null && onSecondaryAction != null) {
            OutlinedButton(
                onClick = onSecondaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.24f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = NimbusTextPrimary,
                ),
            ) {
                if (secondaryActionIcon != null) {
                    Icon(
                        imageVector = secondaryActionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = secondaryActionLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (tertiaryActionLabel != null && onTertiaryAction != null) {
            Text(
                text = tertiaryActionLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.08f))
                    .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = onTertiaryAction,
                        role = Role.Button,
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun NimbusStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = NimbusBlueAccent,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    maxLines: Int = 2,
) {
    val shape = RoundedCornerShape(8.dp)
    val textColor = when {
        emphasized -> NimbusTextPrimary
        tint == NimbusTextTertiary -> NimbusTextSecondary
        else -> tint
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = if (emphasized) 0.16f else 0.10f),
                        Color.White.copy(alpha = 0.04f),
                    ),
                ),
            )
            .border(1.dp, tint.copy(alpha = if (emphasized) 0.26f else 0.16f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NimbusSelectableSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    leadingIcon: ImageVector? = null,
    showIndicator: Boolean = true,
    compact: Boolean = false,
    tint: Color = NimbusBlueAccent,
    maxLines: Int = 1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val selectedLabel = stringResource(R.string.common_selected)
    val notSelectedLabel = stringResource(R.string.common_not_selected)
    val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    val minHeight = if (compact) 40.dp else 48.dp
    val horizontalPadding = if (compact) 12.dp else 14.dp
    val verticalPadding = if (compact) 8.dp else 10.dp
    val containerTop by animateColorAsState(
        targetValue = when {
            selected -> tint.copy(alpha = 0.26f)
            pressed -> tint.copy(alpha = 0.18f)
            focused -> tint.copy(alpha = 0.14f)
            else -> NimbusGlassTop.copy(alpha = 0.64f)
        },
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentTop",
    )
    val containerBottom by animateColorAsState(
        targetValue = if (selected) NimbusGlassBottom else NimbusCardBg,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentBottom",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> tint.copy(alpha = 0.56f)
            focused -> tint.copy(alpha = 0.62f)
            pressed -> tint.copy(alpha = 0.42f)
            else -> NimbusCardBorder
        },
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentBorder",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) tint else NimbusTextTertiary.copy(alpha = 0.42f),
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentIndicator",
    )
    val indicatorSize by animateDpAsState(
        targetValue = if (selected) 8.dp else 5.dp,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentIndicatorSize",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) NimbusTextPrimary else NimbusTextSecondary,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentText",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) NimbusTextPrimary else NimbusTextTertiary,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "segmentIcon",
    )

    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer {
                scaleX = if (pressed) 0.99f else 1f
                scaleY = if (pressed) 0.99f else 1f
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(containerTop, containerBottom),
                ),
            )
            .border(1.dp, borderColor, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                role = role,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (selected) selectedLabel else notSelectedLabel
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .clip(RoundedCornerShape(3.dp))
                        .background(indicatorColor),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NimbusScrollableSegmentRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun InlineNoticeCard(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = NimbusBlueAccent,
    liveRegion: LiveRegionMode? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.12f),
                        NimbusCardBg,
                    ),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $message"
                if (liveRegion != null) {
                    this.liveRegion = liveRegion
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = NimbusTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
