package com.searchaid.ui.feature_onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingContent(
        state = state,
        onComplete = onComplete,
        onSave = viewModel::save,
        onGoogleWebToggle = viewModel::onGoogleWebToggle,
        onGoogleImagesToggle = viewModel::onGoogleImagesToggle,
        onFacebookToggle = viewModel::onFacebookToggle,
        onInstagramToggle = viewModel::onInstagramToggle,
        onTiktokToggle = viewModel::onTiktokToggle,
        onArchivesToggle = viewModel::onArchivesToggle,
        onApiKeyChange = viewModel::onApiKeyChange,
        onCxChange = viewModel::onCxChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    onComplete: () -> Unit,
    onSave: () -> Unit,
    onGoogleWebToggle: (Boolean) -> Unit,
    onGoogleImagesToggle: (Boolean) -> Unit,
    onFacebookToggle: (Boolean) -> Unit,
    onInstagramToggle: (Boolean) -> Unit,
    onTiktokToggle: (Boolean) -> Unit,
    onArchivesToggle: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onCxChange: (String) -> Unit,
) {
    LaunchedEffect(state.saved) {
        if (state.saved) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup SearchAid") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Choose search tools",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "All tools are enabled by default. Disable the ones you don't need.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            SectionHeader("Web Search")
            ToolToggle("Google Web Search", state.googleWebEnabled, onGoogleWebToggle)
            ToolToggle("Google Image Search", state.googleImagesEnabled, onGoogleImagesToggle)
            ToolToggle("Web Archives (Wayback Machine)", state.archivesEnabled, onArchivesToggle)

            Spacer(Modifier.height(8.dp))

            SectionHeader("Social Networks")
            ToolToggle("Facebook", state.facebookEnabled, onFacebookToggle)
            ToolToggle("Instagram", state.instagramEnabled, onInstagramToggle)
            ToolToggle("TikTok", state.tiktokEnabled, onTiktokToggle)

            Spacer(Modifier.height(16.dp))

            SectionHeader("Google API Keys (optional)")
            Text(
                "Required for web and social search. You can add them later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.googleApiKey,
                onValueChange = onApiKeyChange,
                label = { Text("Google API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            OutlinedTextField(
                value = state.googleCx,
                onValueChange = onCxChange,
                label = { Text("Search Engine ID (CX)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun ToolToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
