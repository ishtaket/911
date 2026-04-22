package com.searchaid.ui.feature_outreach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutreachScreen(
    onBack: () -> Unit,
    viewModel: OutreachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OutreachScreenContent(
        state = state,
        onBack = onBack,
        onShowAddDialog = viewModel::showAddDialog,
        onDismissAddDialog = viewModel::dismissAddDialog,
        onMarkResponded = viewModel::markResponded,
        onChannelChange = viewModel::onChannelChange,
        onRecipientChange = viewModel::onRecipientChange,
        onMessageTextChange = viewModel::onMessageTextChange,
        onSubmitMessage = viewModel::submitMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutreachScreenContent(
    state: OutreachState,
    onBack: () -> Unit,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onMarkResponded: (Long) -> Unit,
    onChannelChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onMessageTextChange: (String) -> Unit,
    onSubmitMessage: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outreach") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onShowAddDialog) {
                Icon(Icons.Default.Add, contentDescription = "Add message")
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No outreach messages yet. Tap + to send one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        onMarkResponded = { onMarkResponded(message.id) },
                    )
                }
            }
        }
    }

    if (state.showAddDialog) {
        AddMessageDialog(
            form = state.addForm,
            onChannelChange = onChannelChange,
            onRecipientChange = onRecipientChange,
            onMessageTextChange = onMessageTextChange,
            onSubmit = onSubmitMessage,
            onDismiss = onDismissAddDialog,
        )
    }
}

@Composable
private fun MessageCard(
    message: OutreachMessage,
    onMarkResponded: () -> Unit,
) {
    val statusColor = when (message.status) {
        OutreachStatus.DRAFT -> MaterialTheme.colorScheme.outline
        OutreachStatus.SENT -> MaterialTheme.colorScheme.primary
        OutreachStatus.RESPONDED -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message.channel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    message.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("To: ${message.recipient}", style = MaterialTheme.typography.bodySmall)
            Text(message.messageText, style = MaterialTheme.typography.bodyMedium, maxLines = 4)

            if (message.status == OutreachStatus.SENT) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onMarkResponded) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Mark responded",
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMessageDialog(
    form: AddOutreachForm,
    onChannelChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onMessageTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.channel,
                    onValueChange = onChannelChange,
                    label = { Text("Channel *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.recipient,
                    onValueChange = onRecipientChange,
                    label = { Text("Recipient *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.messageText,
                    onValueChange = onMessageTextChange,
                    label = { Text("Message *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
