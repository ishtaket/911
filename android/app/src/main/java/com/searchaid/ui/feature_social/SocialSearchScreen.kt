package com.searchaid.ui.feature_social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.SocialSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialSearchScreen(
    onBack: () -> Unit,
    viewModel: SocialSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SocialSearchScreenContent(
        state = state,
        onBack = onBack,
        onRunSearch = viewModel::runSearch,
        onSelectPlatform = viewModel::selectPlatform,
        onPromoteToLead = viewModel::promoteToLead,
        onDismiss = viewModel::dismissResult,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SocialSearchScreenContent(
    state: SocialSearchState,
    onBack: () -> Unit,
    onRunSearch: () -> Unit,
    onSelectPlatform: (String?) -> Unit,
    onPromoteToLead: (SocialSearchResult) -> Unit,
    onDismiss: (SocialSearchResult) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Social Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.loading && state.identityPack != null) {
                ExtendedFloatingActionButton(
                    onClick = onRunSearch,
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    text = { Text(if (state.searching) "Searching..." else "Search") },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    // Identity summary
                    item {
                        val pack = state.identityPack
                        if (pack != null) {
                            Text(
                                "Identity: ${pack.primaryName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${pack.handles.size} known handles, ${state.sources.size} social sources",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Known handles
                    if (state.sources.isNotEmpty()) {
                        item {
                            Text(
                                "Known Accounts",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                state.sources.filter { it.enabled }.forEach { source ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {},
                                        label = {
                                            Text(
                                                "${source.platform}: ${source.handleOrAlias ?: "?"}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Platform filter
                    if (state.platforms.isNotEmpty()) {
                        item {
                            Text(
                                "Filter by Platform",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilterChip(
                                    selected = state.selectedPlatform == null,
                                    onClick = { onSelectPlatform(null) },
                                    label = { Text("All") },
                                )
                                state.platforms.forEach { platform ->
                                    FilterChip(
                                        selected = state.selectedPlatform == platform,
                                        onClick = { onSelectPlatform(platform) },
                                        label = { Text(platform) },
                                    )
                                }
                            }
                        }
                    }

                    // Results
                    if (state.searching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (state.filteredResults.isNotEmpty()) {
                        item {
                            Text(
                                "${state.filteredResults.size} profiles found",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(state.filteredResults, key = { it.profileUrl }) { result ->
                            SocialResultCard(
                                result = result,
                                onPromote = { onPromoteToLead(result) },
                                onDismiss = { onDismiss(result) },
                            )
                        }
                    } else if (state.results.isEmpty() && !state.searching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Tap Search to find social profiles",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialResultCard(
    result: SocialSearchResult,
    onPromote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDismissed = result.status == SearchResultStatus.DISMISSED
    val isPromoted = result.status == SearchResultStatus.PROMOTED_TO_LEAD

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPromoted -> MaterialTheme.colorScheme.primaryContainer
                isDismissed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.profileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    result.handle?.let {
                        Text(
                            "@$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        result.platform,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        result.matchType.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isPromoted) {
                        Text(
                            "LEAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            result.snippet?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                result.profileUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!isPromoted && !isDismissed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = onPromote) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Promote to lead",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
