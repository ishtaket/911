package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.remote.Rescue911Api
import com.rescue911.osint.data.remote.dto.CreateCaseRequestDto
import com.rescue911.osint.data.remote.dto.CreatePersonDto
import com.rescue911.osint.navigation.Routes
import com.rescue911.osint.ui.components.ActionResultBanner
import com.rescue911.osint.ui.components.BannerKind
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CreateCaseViewModel @Inject constructor(
    private val api: Rescue911Api,
) : ViewModel() {
    sealed interface CreateState {
        data object Idle : CreateState
        data object Submitting : CreateState
        data class Created(val newCaseId: String) : CreateState
        data class Failed(val message: String) : CreateState
    }

    private val _state = MutableStateFlow<CreateState>(CreateState.Idle)
    val state: StateFlow<CreateState> = _state.asStateFlow()

    fun create(
        title: String,
        personName: String,
        lastSeen: String?,
        language: String,
        notes: String?,
    ) {
        if (title.isBlank() || personName.isBlank()) {
            _state.value = CreateState.Failed("Title and full name are required.")
            return
        }
        _state.value = CreateState.Submitting
        viewModelScope.launch {
            val req = CreateCaseRequestDto(
                title = title.trim(),
                description = notes?.takeIf { it.isNotBlank() },
                person = CreatePersonDto(fullName = personName.trim()),
                lastSeenLocation = lastSeen?.takeIf { it.isNotBlank() },
                languages = listOf(language),
                riskNotes = notes?.takeIf { it.isNotBlank() },
                operatorId = "operator",
            )
            runCatching { api.createCase(req) }
                .onSuccess { _state.value = CreateState.Created(it.id) }
                .onFailure {
                    _state.value = CreateState.Failed(
                        "${it.javaClass.simpleName}: ${it.message ?: "no detail"}"
                    )
                }
        }
    }
}

@Composable
fun CreateCaseScreen(
    navController: NavHostController,
    padding: PaddingValues,
    vm: CreateCaseViewModel = hiltViewModel(),
) {
    var title by remember { mutableStateOf("") }
    var personName by remember { mutableStateOf("") }
    var lastSeen by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    val state by vm.state.collectAsState()

    // After successful creation: navigate to the new case detail and replace
    // CreateCase in the back stack so Back from detail goes to the list.
    if (state is CreateCaseViewModel.CreateState.Created) {
        val id = (state as CreateCaseViewModel.CreateState.Created).newCaseId
        androidx.compose.runtime.LaunchedEffect(id) {
            navController.navigate(Routes.caseDetail(id)) {
                popUpTo(Routes.CASES) { inclusive = false }
            }
        }
    }

    ScreenScaffold(stringResource(R.string.case_create), padding) {
        when (val s = state) {
            is CreateCaseViewModel.CreateState.Submitting ->
                ActionResultBanner(message = stringResource(R.string.create_case_submitting), kind = BannerKind.INFO)
            is CreateCaseViewModel.CreateState.Failed ->
                ActionResultBanner(message = stringResource(R.string.create_case_failed, s.message), kind = BannerKind.ERROR)
            is CreateCaseViewModel.CreateState.Created ->
                ActionResultBanner(message = stringResource(R.string.create_case_created, s.newCaseId), kind = BannerKind.SUCCESS)
            else -> Unit
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.case_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = personName,
                onValueChange = { personName = it },
                label = { Text(stringResource(R.string.person_full_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = lastSeen,
                onValueChange = { lastSeen = it },
                label = { Text(stringResource(R.string.case_last_seen)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text(stringResource(R.string.create_case_language_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.create_case_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.create(title, personName, lastSeen, language, notes) },
                enabled = title.isNotBlank() && personName.isNotBlank()
                    && state !is CreateCaseViewModel.CreateState.Submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state is CreateCaseViewModel.CreateState.Submitting)
                        stringResource(R.string.create_case_submitting_short)
                    else
                        stringResource(R.string.action_create)
                )
            }
        }
    }
}
