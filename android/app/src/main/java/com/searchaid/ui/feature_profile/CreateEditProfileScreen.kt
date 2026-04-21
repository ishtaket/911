package com.searchaid.ui.feature_profile

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreateEditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit Profile" else "New Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (!state.loading) {
                FloatingActionButton(onClick = viewModel::save) {
                    Icon(Icons.Default.Check, contentDescription = "Save")
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(padding).padding(32.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))

                SectionHeader("Basic Info")
                FormField("Full name *", state.name, viewModel::onNameChange)
                FormField("Age", state.age, viewModel::onAgeChange, keyboardType = KeyboardType.Number)
                FormField("Condition / Diagnosis", state.condition, viewModel::onConditionChange)
                FormField("Distinguishing features", state.distinguishingFeatures, viewModel::onFeaturesChange, singleLine = false)

                SectionHeader("Behavior")
                FormField("Habits", state.habits, viewModel::onHabitsChange, singleLine = false)
                FormField("Known locations", state.knownLocations, viewModel::onLocationsChange, singleLine = false)

                SectionHeader("Identity")
                FormField("Aliases (comma-separated)", state.aliases, viewModel::onAliasesChange)
                FormField("Nicknames (comma-separated)", state.nicknames, viewModel::onNicknamesChange)
                FormField("Emails (comma-separated)", state.emails, viewModel::onEmailsChange, keyboardType = KeyboardType.Email)
                FormField("Phones (comma-separated)", state.phones, viewModel::onPhonesChange, keyboardType = KeyboardType.Phone)

                SectionHeader("Family")
                FormField("Family notes", state.familyNotes, viewModel::onFamilyNotesChange, singleLine = false)

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
