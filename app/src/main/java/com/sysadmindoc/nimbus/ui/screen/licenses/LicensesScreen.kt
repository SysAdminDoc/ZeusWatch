package com.sysadmindoc.nimbus.ui.screen.licenses

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.nimbus.R
import com.sysadmindoc.nimbus.data.repository.OssNotice
import com.sysadmindoc.nimbus.data.repository.OssProviderNotice
import com.sysadmindoc.nimbus.ui.component.PredictiveBackScaffold
import com.sysadmindoc.nimbus.ui.component.ScreenHeader
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusNavyDark
import com.sysadmindoc.nimbus.ui.theme.NimbusSurfaceVariant
import com.sysadmindoc.nimbus.ui.theme.NimbusTextPrimary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusTextTertiary

/**
 * Open-source notices and weather-data attribution.
 *
 * Content comes from a generated asset rather than anything hand-written in
 * this file, so the list cannot drift from the dependencies actually shipped.
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val filtered = remember(state.notices, query) { state.notices.filter(query) }

    LaunchedEffect(Unit) { viewModel.load() }

    val openUrl: (String) -> Unit = { url ->
        // A device with no browser must not crash the licences screen.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    PredictiveBackScaffold(onBack = onBack) {
        Scaffold(
            containerColor = NimbusNavyDark,
            topBar = {
                ScreenHeader(
                    title = stringResource(R.string.licenses_title),
                    subtitle = stringResource(
                        R.string.licenses_subtitle,
                        state.notices.dependencies.size,
                        state.notices.providers.size,
                    ),
                    onBack = onBack,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.licenses_search)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (filtered.isEmpty) {
                    item {
                        Text(
                            text = stringResource(R.string.licenses_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NimbusTextSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                if (filtered.providers.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.licenses_section_providers)) }
                    items(filtered.providers, key = { "provider-${it.name}" }) { provider ->
                        ProviderRow(provider, onClick = { openUrl(provider.url) })
                    }
                }
                if (filtered.dependencies.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.licenses_section_dependencies)) }
                    items(filtered.dependencies, key = { "dep-${it.name}" }) { notice ->
                        DependencyRow(notice, onClick = { openUrl(notice.url) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = NimbusTextTertiary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun DependencyRow(notice: OssNotice, onClick: () -> Unit) {
    NoticeCard(onClick = onClick) {
        Text(
            text = notice.name,
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = notice.version,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusTextTertiary,
            )
            Text(
                text = notice.license,
                style = MaterialTheme.typography.labelSmall,
                color = NimbusBlueAccent,
            )
        }
    }
}

@Composable
private fun ProviderRow(provider: OssProviderNotice, onClick: () -> Unit) {
    NoticeCard(onClick = onClick) {
        Text(
            text = provider.name,
            style = MaterialTheme.typography.bodyMedium,
            color = NimbusTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = provider.license,
            style = MaterialTheme.typography.labelSmall,
            color = NimbusBlueAccent,
        )
    }
}

@Composable
private fun NoticeCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    val openLabel = stringResource(R.string.licenses_open_link)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NimbusSurfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, NimbusBlueAccent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = openLabel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        content()
    }
}
