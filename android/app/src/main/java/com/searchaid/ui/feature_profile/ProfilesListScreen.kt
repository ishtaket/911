package com.searchaid.ui.feature_profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searchaid.domain.model.PersonProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesListScreen(
    onProfileClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: ProfilesListViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    ProfilesListScreenContent(
        profiles = profiles,
        onProfileClick = onProfileClick,
        onCreateClick = onCreateClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesListScreenContent(
    profiles: List<PersonProfile>,
    onProfileClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "Create profile")
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No profiles yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tap + to create a person profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileListItem(profile = profile, onClick = { onProfileClick(profile.id) })
                }
            }
        }
    }
}

@Composable
private fun ProfileListItem(profile: PersonProfile, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = {
            Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val parts = listOfNotNull(
                profile.age?.let { "${it} y.o." },
                profile.condition,
            )
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Icon(Icons.Default.Person, contentDescription = null)
        },
    )
}
