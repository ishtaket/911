package com.searchaid.ui.feature_case

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchaid.domain.model.CaseStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCaseDashboardScreen(
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit,
    onOpenLeads: (Long) -> Unit,
    onOpenWitness: (Long) -> Unit,
    onOpenOutreach: (Long) -> Unit,
    onOpenWebSearch: (Long) -> Unit,
    onOpenSocialSearch: (Long) -> Unit,
    onOpenAudit: (Long) -> Unit,
    viewModel: ActiveCaseDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActiveCaseDashboardContent(
        state = state,
        caseId = viewModel.caseId,
        onBack = onBack,
        onOpenMap = onOpenMap,
        onOpenLeads = onOpenLeads,
        onOpenWitness = onOpenWitness,
        onOpenOutreach = onOpenOutreach,
        onOpenWebSearch = onOpenWebSearch,
        onOpenSocialSearch = onOpenSocialSearch,
        onOpenAudit = onOpenAudit,
        onMarkFound = viewModel::markFound,
        onCloseCase = viewModel::closeCase,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveCaseDashboardContent(
    state: CaseDashboardState,
    caseId: Long,
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit,
    onOpenLeads: (Long) -> Unit,
    onOpenWitness: (Long) -> Unit,
    onOpenOutreach: (Long) -> Unit,
    onOpenWebSearch: (Long) -> Unit,
    onOpenSocialSearch: (Long) -> Unit,
    onOpenAudit: (Long) -> Unit,
    onMarkFound: () -> Unit,
    onCloseCase: () -> Unit,
) {

    val topBarColor = when (state.case_?.status) {
        CaseStatus.ACTIVE -> MaterialTheme.colorScheme.error
        CaseStatus.FOUND -> MaterialTheme.colorScheme.tertiary
        CaseStatus.CLOSED, null -> MaterialTheme.colorScheme.surfaceVariant
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.case_?.status) {
                            CaseStatus.ACTIVE -> "ACTIVE CASE"
                            CaseStatus.FOUND -> "FOUND"
                            CaseStatus.CLOSED -> "CLOSED"
                            null -> "Case"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = MaterialTheme.colorScheme.onError,
                    navigationIconContentColor = MaterialTheme.colorScheme.onError,
                ),
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.case_ == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Case not found")
                }
            }
            else -> {
                val case_ = state.case_!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))

                    // Person summary
                    state.person?.let { person ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    person.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                val details = listOfNotNull(
                                    person.age?.let { "$it y.o." },
                                    person.condition,
                                )
                                if (details.isNotEmpty()) {
                                    Text(
                                        details.joinToString(" · "),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                person.distinguishingFeatures?.let {
                                    Text(
                                        "Features: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }

                    // Case info
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Case Info",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))

                            val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                            Text("Created: ${fmt.format(Date(case_.createdAt))}")

                            case_.lastSeenTime?.let {
                                Text("Last seen: ${fmt.format(Date(it))}")
                            }
                            case_.lastSeenLocationName?.let {
                                Text("Location: $it")
                            }
                            if (case_.lastSeenLat != null && case_.lastSeenLon != null) {
                                Text("Coordinates: %.5f, %.5f".format(case_.lastSeenLat, case_.lastSeenLon))
                            }
                            case_.clothesDescription?.let {
                                Text("Clothing: $it")
                            }
                            case_.notes?.let {
                                Text("Notes: $it")
                            }
                        }
                    }

                    // Action buttons
                    Text(
                        "Actions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    ActionButton(
                        icon = Icons.Default.LocationOn,
                        label = "Search Map",
                        subtitle = "Zones, leads, witness markers",
                        onClick = { onOpenMap(caseId) },
                    )
                    ActionButton(
                        icon = Icons.Default.Search,
                        label = "Search Leads",
                        subtitle = "Web, social, manual leads",
                        onClick = { onOpenLeads(caseId) },
                    )
                    ActionButton(
                        icon = Icons.Default.Person,
                        label = "Witness Reports",
                        subtitle = "Sightings and tips",
                        onClick = { onOpenWitness(caseId) },
                    )
                    ActionButton(
                        icon = Icons.Default.MailOutline,
                        label = "Outreach",
                        subtitle = "Messages to groups and contacts",
                        onClick = { onOpenOutreach(caseId) },
                    )
                    ActionButton(
                        icon = Icons.Default.Star,
                        label = "Web Search",
                        subtitle = "Google search + photos, archives",
                        onClick = { onOpenWebSearch(caseId) },
                    )
                    ActionButton(
                        icon = Icons.Default.Person,
                        label = "Social Search",
                        subtitle = "Facebook, Instagram, TikTok profiles",
                        onClick = { onOpenSocialSearch(caseId) },
                    )
                    ActionButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = "Audit Log",
                        subtitle = "Full action journal",
                        onClick = { onOpenAudit(caseId) },
                    )

                    // Status controls
                    if (case_.status == CaseStatus.ACTIVE) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Case Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onMarkFound,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("FOUND")
                            }
                            OutlinedButton(
                                onClick = onCloseCase,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Close Case")
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
