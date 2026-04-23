package com.searchaid.ui.feature_websearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.WebSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(
    onBack: () -> Unit,
    viewModel: WebSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WebSearchScreenContent(
        state = state,
        onBack = onBack,
        onRunSearch = viewModel::runSearch,
        onToggleImages = viewModel::toggleImages,
        onPromoteToLead = viewModel::promoteToLead,
        onDismiss = viewModel::dismissResult,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WebSearchScreenContent(
    state: WebSearchState,
    onBack: () -> Unit,
    onRunSearch: () -> Unit,
    onToggleImages: () -> Unit = {},
    onPromoteToLead: (WebSearchResult) -> Unit,
    onDismiss: (WebSearchResult) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web Search") },
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
                                "${pack.nameVariants.size} name variants, ${pack.handles.size} handles",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Query chips
                    item {
                        Text(
                            "Search Queries",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.queries.take(8).forEach { query ->
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            query,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Search mode toggle
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Text") },
                            )
                            FilterChip(
                                selected = state.includeImages,
                                onClick = onToggleImages,
                                label = { Text("Photos") },
                            )
                        }
                    }

                    // Results
                    if (state.searching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (state.results.isNotEmpty()) {
                        // Image results carousel
                        if (state.imageResults.isNotEmpty()) {
                            item {
                                Text(
                                    "${state.imageResults.size} photos found",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.imageResults, key = { it.url }) { result ->
                                        ImageResultCard(
                                            result = result,
                                            onPromote = { onPromoteToLead(result) },
                                            onDismiss = { onDismiss(result) },
                                        )
                                    }
                                }
                            }
                        }

                        // Text results
                        if (state.textResults.isNotEmpty()) {
                            item {
                                Text(
                                    "${state.textResults.size} text results found",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(state.textResults, key = { it.url }) { result ->
                                SearchResultCard(
                                    result = result,
                                    onPromote = { onPromoteToLead(result) },
                                    onDismiss = { onDismiss(result) },
                                )
                            }
                        }
                    } else if (state.queries.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Tap Search to run queries",
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
private fun ImageResultCard(
    result: WebSearchResult,
    onPromote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isPromoted = result.status == SearchResultStatus.PROMOTED_TO_LEAD
    val isDismissed = result.status == SearchResultStatus.DISMISSED

    Card(
        modifier = Modifier.width(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPromoted -> MaterialTheme.colorScheme.primaryContainer
                isDismissed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column {
            val imageUrl = result.thumbnailUrl ?: result.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = result.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isPromoted) {
                    Text(
                        "LEAD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!isPromoted && !isDismissed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onPromote, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Promote to lead",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: WebSearchResult,
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
                // Thumbnail if available
                val thumb = result.thumbnailUrl
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
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
            Spacer(Modifier.height(4.dp))
            Text(
                result.snippet,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                result.url,
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
