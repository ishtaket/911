package com.searchaid.ui.feature_case

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCaseDashboardScreen(
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit,
    onOpenLeads: (Long) -> Unit,
    onOpenWitness: (Long) -> Unit,
    onOpenOutreach: (Long) -> Unit,
    onOpenAudit: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Case") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Case Dashboard — placeholder")
            Button(onClick = { onOpenMap(0) }, modifier = Modifier.fillMaxWidth()) { Text("Search Map") }
            Button(onClick = { onOpenLeads(0) }, modifier = Modifier.fillMaxWidth()) { Text("Leads") }
            Button(onClick = { onOpenWitness(0) }, modifier = Modifier.fillMaxWidth()) { Text("Witness Reports") }
            Button(onClick = { onOpenOutreach(0) }, modifier = Modifier.fillMaxWidth()) { Text("Outreach") }
            Button(onClick = { onOpenAudit(0) }, modifier = Modifier.fillMaxWidth()) { Text("Audit Log") }
        }
    }
}
