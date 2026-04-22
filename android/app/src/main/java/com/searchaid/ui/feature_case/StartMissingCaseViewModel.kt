package com.searchaid.ui.feature_case

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.StartMissingCaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartCaseFormState(
    val person: PersonProfile? = null,
    val lastSeenLocationName: String = "",
    val lastSeenLat: String = "",
    val lastSeenLon: String = "",
    val clothesDescription: String = "",
    val notes: String = "",
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val createdCaseId: Long? = null,
)

@HiltViewModel
class StartMissingCaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProfile: GetPersonProfileUseCase,
    private val startCase: StartMissingCaseUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    private val profileId: Long = savedStateHandle["profileId"] ?: -1L

    private val _state = MutableStateFlow(StartCaseFormState())
    val state: StateFlow<StartCaseFormState> = _state

    init {
        viewModelScope.launch {
            val profile = getProfile(profileId)
            _state.update { it.copy(person = profile, loading = false) }
        }
    }

    fun onLocationNameChange(v: String) { _state.update { it.copy(lastSeenLocationName = v) } }
    fun onLatChange(v: String) { _state.update { it.copy(lastSeenLat = v) } }
    fun onLonChange(v: String) { _state.update { it.copy(lastSeenLon = v) } }
    fun onClothesChange(v: String) { _state.update { it.copy(clothesDescription = v) } }
    fun onNotesChange(v: String) { _state.update { it.copy(notes = v) } }

    fun submit() {
        val s = _state.value
        if (s.submitting || s.person == null) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }

            val case = MissingCase(
                personId = profileId,
                lastSeenTime = System.currentTimeMillis(),
                lastSeenLocationName = s.lastSeenLocationName.trim().ifBlank { null },
                lastSeenLat = s.lastSeenLat.toDoubleOrNull(),
                lastSeenLon = s.lastSeenLon.toDoubleOrNull(),
                clothesDescription = s.clothesDescription.trim().ifBlank { null },
                notes = s.notes.trim().ifBlank { null },
                operatorId = null,
            )

            val caseId = startCase(case)
            logAction(
                "CASE_STARTED",
                caseId = caseId,
                details = "Missing case started for ${s.person!!.name}, last seen: ${s.lastSeenLocationName.ifBlank { "unknown" }}",
            )
            _state.update { it.copy(submitting = false, createdCaseId = caseId) }
        }
    }
}
