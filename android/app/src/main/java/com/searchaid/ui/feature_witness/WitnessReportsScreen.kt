package com.searchaid.ui.feature_witness

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
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WitnessReportsScreen(
    onBack: () -> Unit,
    viewModel: WitnessReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WitnessReportsScreenContent(
        state = state,
        onBack = onBack,
        onShowAddDialog = viewModel::showAddDialog,
        onDismissAddDialog = viewModel::dismissAddDialog,
        onVerifyReport = viewModel::verifyReport,
        onRejectReport = viewModel::rejectReport,
        onSourceNameChange = viewModel::onSourceNameChange,
        onSourceTypeChange = viewModel::onSourceTypeChange,
        onTextChange = viewModel::onTextChange,
        onLocationNameChange = viewModel::onLocationNameChange,
        onLatChange = viewModel::onLatChange,
        onLonChange = viewModel::onLonChange,
        onConfidenceChange = viewModel::onConfidenceChange,
        onSubmitReport = viewModel::submitReport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WitnessReportsScreenContent(
    state: WitnessReportsState,
    onBack: () -> Unit,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onVerifyReport: (Long) -> Unit,
    onRejectReport: (Long) -> Unit,
    onSourceNameChange: (String) -> Unit,
    onSourceTypeChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onLocationNameChange: (String) -> Unit,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    onConfidenceChange: (String) -> Unit,
    onSubmitReport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Witness Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onShowAddDialog) {
                Icon(Icons.Default.Add, contentDescription = "Add report")
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No witness reports yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(state.reports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        onVerify = { onVerifyReport(report.id) },
                        onReject = { onRejectReport(report.id) },
                    )
                }
            }
        }
    }

    if (state.showAddDialog) {
        AddReportDialog(
            form = state.addForm,
            onSourceNameChange = onSourceNameChange,
            onSourceTypeChange = onSourceTypeChange,
            onTextChange = onTextChange,
            onLocationNameChange = onLocationNameChange,
            onLatChange = onLatChange,
            onLonChange = onLonChange,
            onConfidenceChange = onConfidenceChange,
            onSubmit = onSubmitReport,
            onDismiss = onDismissAddDialog,
        )
    }
}

@Composable
private fun ReportCard(
    report: WitnessReport,
    onVerify: () -> Unit,
    onReject: () -> Unit,
) {
    val statusColor = when (report.status) {
        ReportStatus.NEW -> MaterialTheme.colorScheme.primary
        ReportStatus.VERIFIED -> MaterialTheme.colorScheme.tertiary
        ReportStatus.REJECTED -> MaterialTheme.colorScheme.error
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
                report.sourceName?.let {
                    Text(
                        "Source: $it",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } ?: Text(
                    "Anonymous",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    report.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            report.sourceType?.let {
                Text("Type: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text(report.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            report.possibleLocationName?.let {
                Text("Location: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (report.confidence > 0f) {
                Text(
                    "Confidence: ${"%.0f".format(report.confidence * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (report.status == ReportStatus.NEW) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onVerify) {
                        Icon(Icons.Default.Check, contentDescription = "Verify", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onReject) {
                        Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddReportDialog(
    form: AddReportForm,
    onSourceNameChange: (String) -> Unit,
    onSourceTypeChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onLocationNameChange: (String) -> Unit,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    onConfidenceChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.sourceName,
                    onValueChange = onSourceNameChange,
                    label = { Text("Source name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.sourceType,
                    onValueChange = onSourceTypeChange,
                    label = { Text("Source type (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.text,
                    onValueChange = onTextChange,
                    label = { Text("Report text *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
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
                    label = { Text("Confidence 0.0\u20131.0") },
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
