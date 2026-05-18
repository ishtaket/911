package com.pca.assistant.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pca.assistant.R
import com.pca.assistant.service.ListeningService
import com.pca.assistant.service.ListeningState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val s by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dash_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.hist_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // State + quick toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stateLabel(s.listening),
                            style = MaterialTheme.typography.titleLarge,
                            color = stateColor(s.listening)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dash_llm_provider, s.provider),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    when (s.listening) {
                        ListeningState.LISTENING -> {
                            IconButton(onClick = {
                                ListeningService.sendAction(context, ListeningService.ACTION_PAUSE)
                            }) {
                                Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.dash_quick_pause))
                            }
                        }
                        else -> {
                            IconButton(onClick = {
                                ListeningService.start(context)
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.dash_quick_resume))
                            }
                        }
                    }
                }
            }

            if (s.bridgeUrlMissing) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.settings_bridge_url_missing),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(modifier = Modifier.weight(1f), value = s.windowsToday.toString(), label = stringResource(R.string.dash_windows_today, s.windowsToday).substringBefore(":"))
                MetricCard(modifier = Modifier.weight(1f), value = s.interventionsToday.toString(), label = stringResource(R.string.dash_interventions_today, s.interventionsToday).substringBefore(":"))
                MetricCard(modifier = Modifier.weight(1f), value = s.openThreadsCount.toString(), label = stringResource(R.string.dash_open_threads, s.openThreadsCount).substringBefore(":"))
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.dash_recent_window), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (s.recentWindows.isEmpty()) {
                Text(stringResource(R.string.dash_no_windows), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.recentWindows) { w ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = "${java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(w.startTs))} → ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(w.endTs))}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = when {
                                        w.skipped -> "skipped: ${w.skipReason ?: ""}"
                                        w.sentToLlm -> "sent → ${w.llmProvider ?: "?"} (${w.latencyMs ?: 0} ms)"
                                        else -> "queued"
                                    },
                                    style = MaterialTheme.typography.bodySmall
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
private fun MetricCard(modifier: Modifier = Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label.trim(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun stateLabel(state: ListeningState): String = when (state) {
    ListeningState.LISTENING -> stringResource(R.string.dash_state_listening)
    ListeningState.PAUSED -> stringResource(R.string.dash_state_paused)
    ListeningState.STOPPED -> stringResource(R.string.dash_state_stopped)
}

@Composable
private fun stateColor(state: ListeningState) = when (state) {
    ListeningState.LISTENING -> MaterialTheme.colorScheme.primary
    ListeningState.PAUSED -> MaterialTheme.colorScheme.secondary
    ListeningState.STOPPED -> MaterialTheme.colorScheme.tertiary
}
