package com.sysadmindoc.nimbus.ui.screen.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.StarterCardSet
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.ui.component.InlineNoticeCard
import com.sysadmindoc.nimbus.ui.component.NimbusSelectableSegment
import com.sysadmindoc.nimbus.ui.component.NimbusStatusBadge
import com.sysadmindoc.nimbus.ui.theme.NimbusBackgroundGradient
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBg
import com.sysadmindoc.nimbus.ui.theme.NimbusCardBorder
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassBottom
import com.sysadmindoc.nimbus.ui.theme.NimbusGlassTop
import com.sysadmindoc.nimbus.ui.theme.NimbusSuccess
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning

private const val ONBOARDING_STEP_COUNT = 3

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var tempUnitName by rememberSaveable { mutableStateOf(TempUnit.FAHRENHEIT.name) }
    var starterCardSetName by rememberSaveable { mutableStateOf(StarterCardSet.STANDARD.name) }
    val tempUnit = enumValueOrDefault(tempUnitName, TempUnit.FAHRENHEIT)
    val starterCardSet = enumValueOrDefault(starterCardSetName, StarterCardSet.STANDARD)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NimbusBackgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingPanel(
                step = step,
                tempUnit = tempUnit,
                starterCardSet = starterCardSet,
                isSaving = isSaving,
                onTempUnitSelected = { tempUnitName = it.name },
                onStarterCardSetSelected = { starterCardSetName = it.name },
                onBack = { step = (step - 1).coerceAtLeast(0) },
                onNext = {
                    if (step < ONBOARDING_STEP_COUNT - 1) {
                        step += 1
                    } else {
                        viewModel.complete(tempUnit, starterCardSet, onComplete)
                    }
                },
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T {
    return enumValues<T>().firstOrNull { it.name == name } ?: default
}

@Composable
private fun OnboardingPanel(
    step: Int,
    tempUnit: TempUnit,
    starterCardSet: StarterCardSet,
    isSaving: Boolean,
    onTempUnitSelected: (TempUnit) -> Unit,
    onStarterCardSetSelected: (StarterCardSet) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        OnboardingHeader(step)
        OnboardingStepCard(
            step = step,
            tempUnit = tempUnit,
            starterCardSet = starterCardSet,
            onTempUnitSelected = onTempUnitSelected,
            onStarterCardSetSelected = onStarterCardSetSelected,
        )
        OnboardingActions(
            step = step,
            isSaving = isSaving,
            onBack = onBack,
            onNext = onNext,
        )
    }
}

@Composable
private fun OnboardingHeader(step: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimbusStatusBadge(
            text = stringResource(R.string.onboarding_progress, step + 1, ONBOARDING_STEP_COUNT),
            tint = NimbusBlueAccent,
            emphasized = true,
        )
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            color = NimbusTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextSecondary,
        )
        OnboardingProgress(step)
    }
}

@Composable
private fun OnboardingProgress(step: Int) {
    val progressLabel = stringResource(R.string.onboarding_progress, step + 1, ONBOARDING_STEP_COUNT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = progressLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(ONBOARDING_STEP_COUNT) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (index <= step) NimbusBlueAccent else NimbusTextTertiary.copy(alpha = 0.22f),
                    ),
            )
        }
    }
}

@Composable
private fun OnboardingStepCard(
    step: Int,
    tempUnit: TempUnit,
    starterCardSet: StarterCardSet,
    onTempUnitSelected: (TempUnit) -> Unit,
    onStarterCardSetSelected: (StarterCardSet) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NimbusGlassTop.copy(alpha = 0.88f),
                        NimbusCardBg,
                        NimbusGlassBottom,
                    ),
                ),
            )
            .border(1.dp, NimbusCardBorder, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Crossfade(
            targetState = step,
            animationSpec = tween(durationMillis = 180),
            label = "onboardingStep",
        ) { activeStep ->
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (activeStep) {
                    0 -> LocationStep()
                    1 -> UnitsStep(tempUnit, onTempUnitSelected)
                    else -> CardSetStep(starterCardSet, onStarterCardSetSelected)
                }
            }
        }
    }
}

@Composable
private fun LocationStep() {
    OnboardingStepTitle(
        icon = Icons.Filled.LocationOn,
        title = stringResource(R.string.onboarding_location_title),
        message = stringResource(R.string.onboarding_location_body),
        tint = NimbusWarning,
    )
    InlineNoticeCard(
        title = stringResource(R.string.onboarding_location_notice_title),
        message = stringResource(R.string.onboarding_location_notice_body),
        icon = Icons.Filled.Check,
        tint = NimbusSuccess,
    )
}

@Composable
private fun UnitsStep(
    selected: TempUnit,
    onSelected: (TempUnit) -> Unit,
) {
    OnboardingStepTitle(
        icon = Icons.Filled.Straighten,
        title = stringResource(R.string.onboarding_units_title),
        message = stringResource(R.string.onboarding_units_body),
        tint = NimbusBlueAccent,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TempUnit.entries.forEach { unit ->
            NimbusSelectableSegment(
                label = stringResource(unit.onboardingLabelRes()),
                selected = selected == unit,
                onClick = { onSelected(unit) },
                modifier = Modifier.weight(1f),
                role = Role.RadioButton,
                leadingIcon = Icons.Filled.Tune,
                maxLines = 2,
            )
        }
    }
    Text(
        text = stringResource(selected.unitBundleDescriptionRes()),
        style = MaterialTheme.typography.bodySmall,
        color = NimbusTextSecondary,
    )
}

@Composable
private fun CardSetStep(
    selected: StarterCardSet,
    onSelected: (StarterCardSet) -> Unit,
) {
    OnboardingStepTitle(
        icon = Icons.Filled.Dashboard,
        title = stringResource(R.string.onboarding_cards_title),
        message = stringResource(R.string.onboarding_cards_body),
        tint = NimbusBlueAccent,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StarterCardSet.entries.forEach { set ->
            CardSetOption(
                set = set,
                selected = selected == set,
                onClick = { onSelected(set) },
            )
        }
    }
}

@Composable
private fun OnboardingStepTitle(
    icon: ImageVector,
    title: String,
    message: String,
    tint: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f))
                .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NimbusTextPrimary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = NimbusTextSecondary,
            )
        }
    }
}

@Composable
private fun CardSetOption(
    set: StarterCardSet,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedLabel = stringResource(R.string.common_selected)
    val notSelectedLabel = stringResource(R.string.common_not_selected)
    val setLabel = stringResource(set.labelRes())
    val setDescription = stringResource(set.descriptionRes())
    val setCount = stringResource(set.countRes())
    val tint = when (set) {
        StarterCardSet.MINIMAL -> NimbusSuccess
        StarterCardSet.STANDARD -> NimbusBlueAccent
        StarterCardSet.EVERYTHING -> NimbusWarning
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (selected) {
                        listOf(tint.copy(alpha = 0.20f), NimbusGlassBottom)
                    } else {
                        listOf(NimbusGlassTop.copy(alpha = 0.56f), NimbusCardBg)
                    },
                ),
            )
            .border(1.dp, if (selected) tint.copy(alpha = 0.46f) else NimbusCardBorder, shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$setLabel, $setDescription"
                stateDescription = if (selected) selectedLabel else notSelectedLabel
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (selected) tint else NimbusTextTertiary.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = NimbusTextPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = setLabel,
                style = MaterialTheme.typography.titleMedium,
                color = NimbusTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = setDescription,
                style = MaterialTheme.typography.bodySmall,
                color = NimbusTextSecondary,
            )
        }
        Text(
            text = setCount,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tint else NimbusTextTertiary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OnboardingActions(
    step: Int,
    isSaving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            enabled = step > 0 && !isSaving,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, NimbusCardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = NimbusTextPrimary,
                disabledContentColor = NimbusTextTertiary.copy(alpha = 0.44f),
            ),
        ) {
            Text(stringResource(R.string.common_back))
        }
        Button(
            onClick = onNext,
            enabled = !isSaving,
            modifier = Modifier
                .weight(1.3f)
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NimbusBlueAccent,
                contentColor = NimbusTextPrimary,
                disabledContainerColor = NimbusCardBorder,
                disabledContentColor = NimbusTextTertiary,
            ),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = NimbusTextPrimary,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = if (step == ONBOARDING_STEP_COUNT - 1) {
                    stringResource(R.string.onboarding_finish)
                } else {
                    stringResource(R.string.onboarding_continue)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun TempUnit.onboardingLabelRes(): Int = when (this) {
    TempUnit.FAHRENHEIT -> R.string.onboarding_units_fahrenheit
    TempUnit.CELSIUS -> R.string.onboarding_units_celsius
}

private fun TempUnit.unitBundleDescriptionRes(): Int = when (this) {
    TempUnit.FAHRENHEIT -> R.string.onboarding_units_fahrenheit_desc
    TempUnit.CELSIUS -> R.string.onboarding_units_celsius_desc
}

private fun StarterCardSet.labelRes(): Int = when (this) {
    StarterCardSet.MINIMAL -> R.string.onboarding_cards_minimal
    StarterCardSet.STANDARD -> R.string.onboarding_cards_standard
    StarterCardSet.EVERYTHING -> R.string.onboarding_cards_everything
}

private fun StarterCardSet.descriptionRes(): Int = when (this) {
    StarterCardSet.MINIMAL -> R.string.onboarding_cards_minimal_desc
    StarterCardSet.STANDARD -> R.string.onboarding_cards_standard_desc
    StarterCardSet.EVERYTHING -> R.string.onboarding_cards_everything_desc
}

private fun StarterCardSet.countRes(): Int = when (this) {
    StarterCardSet.MINIMAL -> R.string.onboarding_cards_minimal_count
    StarterCardSet.STANDARD -> R.string.onboarding_cards_standard_count
    StarterCardSet.EVERYTHING -> R.string.onboarding_cards_everything_count
}
