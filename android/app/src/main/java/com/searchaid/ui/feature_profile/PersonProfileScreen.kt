package com.searchaid.ui.feature_profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonProfileScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStartCase: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    viewModel: PersonProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val cases by viewModel.cases.collectAsStateWithLifecycle()
    var showAddPlaceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.name ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    profile?.let { p ->
                        IconButton(onClick = { onEdit(p.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            profile?.let { p ->
                ExtendedFloatingActionButton(
                    onClick = { onStartCase(p.id) },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    text = { Text("MISSING") },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            }
        },
    ) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            profile == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Profile not found")
                }
            }
            else -> {
                ProfileContent(
                    profile = profile!!,
                    places = places,
                    cases = cases,
                    onAddPlace = { showAddPlaceDialog = true },
                    onDeletePlace = viewModel::deletePlace,
                    onOpenCase = onOpenCase,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showAddPlaceDialog) {
        AddHistoricalPlaceDialog(
            onDismiss = { showAddPlaceDialog = false },
            onConfirm = { title, lat, lon, source, note ->
                viewModel.addPlace(title, lat, lon, source, note)
                showAddPlaceDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    profile: PersonProfile,
    places: List<HistoricalPlace>,
    cases: List<MissingCase>,
    onAddPlace: () -> Unit,
    onDeletePlace: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Active cases
        val activeCases = cases.filter { it.status == CaseStatus.ACTIVE }
        if (activeCases.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Active Cases",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                    activeCases.forEach { case_ ->
                        TextButton(onClick = { onOpenCase(case_.id) }) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Case from ${fmt.format(Date(case_.createdAt))}")
                        }
                    }
                }
            }
        }

        // Basic info card
        InfoCard("Basic Info") {
            InfoRow("Name", profile.name)
            profile.age?.let { InfoRow("Age", "$it") }
            profile.condition?.let { InfoRow("Condition", it) }
            profile.distinguishingFeatures?.let { InfoRow("Features", it) }
        }

        // Behavior card
        if (profile.habits != null || profile.knownLocations != null) {
            InfoCard("Behavior") {
                profile.habits?.let { InfoRow("Habits", it) }
                profile.knownLocations?.let { InfoRow("Known locations", it) }
            }
        }

        // Identity card
        if (profile.aliases.isNotEmpty() || profile.nicknames.isNotEmpty() ||
            profile.emails.isNotEmpty() || profile.phones.isNotEmpty()
        ) {
            InfoCard("Identity") {
                if (profile.aliases.isNotEmpty()) ChipRow("Aliases", profile.aliases)
                if (profile.nicknames.isNotEmpty()) ChipRow("Nicknames", profile.nicknames)
                if (profile.emails.isNotEmpty()) ChipRow("Emails", profile.emails)
                if (profile.phones.isNotEmpty()) ChipRow("Phones", profile.phones)
            }
        }

        // Historical places card
        InfoCard("Historical Places") {
            if (places.isEmpty()) {
                Text(
                    "No places added yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            places.forEach { place ->
                HistoricalPlaceItem(place = place, onDelete = { onDeletePlace(place.id) })
            }
            TextButton(onClick = onAddPlace) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Add place")
            }
        }

        // Family notes
        profile.familyNotes?.let {
            InfoCard("Family Notes") {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun HistoricalPlaceItem(place: HistoricalPlace, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(place.title) },
        supportingContent = {
            Text(
                "%.5f, %.5f".format(place.lat, place.lon),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        },
        overlineContent = place.source?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
    )
}

@Composable
private fun AddHistoricalPlaceDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, lat: Double, lon: Double, source: String?, note: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val isValid = title.isNotBlank() && lat.toDoubleOrNull() != null && lon.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Historical Place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Place name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text("Latitude *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text("Longitude *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Source") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title.trim(),
                        lat.toDouble(),
                        lon.toDouble(),
                        source.trim().ifBlank { null },
                        note.trim().ifBlank { null },
                    )
                },
                enabled = isValid,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(label: String, items: List<String>) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                AssistChip(onClick = {}, label = { Text(item) })
            }
        }
    }
}
