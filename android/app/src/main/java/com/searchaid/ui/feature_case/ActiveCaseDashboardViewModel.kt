package com.searchaid.ui.feature_case

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdateCaseStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaseDashboardState(
    val case_: MissingCase? = null,
    val person: PersonProfile? = null,
    val loading: Boolean = true,
    val statusUpdated: Boolean = false,
)

@HiltViewModel
class ActiveCaseDashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCase: GetMissingCaseUseCase,
    private val getProfile: GetPersonProfileUseCase,
    private val updateStatus: UpdateCaseStatusUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(CaseDashboardState())
    val state: StateFlow<CaseDashboardState> = _state

    init {
        loadCase()
    }

    private fun loadCase() {
        viewModelScope.launch {
            val case = getCase(caseId)
            val person = case?.let { getProfile(it.personId) }
            _state.update { it.copy(case_ = case, person = person, loading = false) }
        }
    }

    fun markFound() {
        viewModelScope.launch {
            updateStatus(caseId, CaseStatus.FOUND)
            logAction("CASE_FOUND", caseId = caseId, details = "Person found, case marked as FOUND")
            _state.update {
                it.copy(case_ = it.case_?.copy(status = CaseStatus.FOUND), statusUpdated = true)
            }
        }
    }

    fun closeCase() {
        viewModelScope.launch {
            updateStatus(caseId, CaseStatus.CLOSED)
            logAction("CASE_CLOSED", caseId = caseId, details = "Case closed")
            _state.update {
                it.copy(case_ = it.case_?.copy(status = CaseStatus.CLOSED), statusUpdated = true)
            }
        }
    }
}
