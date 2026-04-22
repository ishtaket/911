package com.searchaid.ui.feature_search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadsListScreen(
    onBack: () -> Unit,
    viewModel: LeadsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Leads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog) {
                Icon(Icons.Default.Add, contentDescription = "Add lead")
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.leads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No leads yet. Tap + to add a manual lead.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(state.leads, key = { it.id }) { lead ->
                    LeadCard(
                        lead = lead,
                        onConfirm = { viewModel.confirmLead(lead.id) },
                        onReject = { viewModel.rejectLead(lead.id) },
                    )
                }
            }
        }
    }

    if (state.showAddDialog) {
        AddLeadDialog(
            form = state.addForm,
            onTypeChange = viewModel::onTypeChange,
            onPlatformChange = viewModel::onPlatformChange,
            onMatchedValueChange = viewModel::onMatchedValueChange,
            onTextSnippetChange = viewModel::onTextSnippetChange,
            onLocationNameChange = viewModel::onLocationNameChange,
            onLatChange = viewModel::onLatChange,
            onLonChange = viewModel::onLonChange,
            onConfidenceChange = viewModel::onConfidenceChange,
            onSubmit = viewModel::submitLead,
            onDismiss = viewModel::dismissAddDialog,
        )
    }
}

@Composable
private fun LeadCard(
    lead: SearchLead,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val statusColor = when (lead.status) {
        LeadStatus.NEW -> MaterialTheme.colorScheme.primary
        LeadStatus.CONFIRMED -> MaterialTheme.colorScheme.tertiary
        LeadStatus.REJECTED -> MaterialTheme.colorScheme.error
        LeadStatus.ARCHIVED -> MaterialTheme.colorScheme.outline
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
                    lead.type.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    lead.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            lead.platform?.let {
                Text("Platform: $it", style = MaterialTheme.typography.bodySmall)
            }
            lead.matchedValue?.let {
                Text("Match: $it", style = MaterialTheme.typography.bodyMedium)
            }
            lead.textSnippet?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
            lead.possibleLocationName?.let {
                Text("Location: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (lead.confidence > 0f) {
                Text(
                    "Confidence: ${"%.0f".format(lead.confidence * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (lead.status == LeadStatus.NEW) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onConfirm) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onReject) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLeadDialog(
    form: AddLeadForm,
    onTypeChange: (LeadType) -> Unit,
    onPlatformChange: (String) -> Unit,
    onMatchedValueChange: (String) -> Unit,
    onTextSnippetChange: (String) -> Unit,
    onLocationNameChange: (String) -> Unit,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    onConfidenceChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Lead") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Lead type dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = form.type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LeadType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = { onTypeChange(type); expanded = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = form.platform,
                    onValueChange = onPlatformChange,
                    label = { Text("Platform (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.matchedValue,
                    onValueChange = onMatchedValueChange,
                    label = { Text("Matched value (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.textSnippet,
                    onValueChange = onTextSnippetChange,
                    label = { Text("Description / snippet") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = form.locationName,
                    onValueChange = onLocationNameChange,
                    label = { Text("Location name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = form.lat,
                        onValueChange = onLatChange,
                        label = { Text("Lat") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = form.lon,
                        onValueChange = onLonChange,
                        label = { Text("Lon") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = form.confidence,
                    onValueChange = onConfidenceChange,
                    label = { Text("Confidence 0.0–1.0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
