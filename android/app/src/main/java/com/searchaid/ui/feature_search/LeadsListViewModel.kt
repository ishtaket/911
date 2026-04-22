package com.searchaid.ui.feature_search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.usecase.AddLeadUseCase
import com.searchaid.domain.usecase.GetLeadsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdateLeadStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeadsListState(
    val leads: List<SearchLead> = emptyList(),
    val loading: Boolean = true,
    val showAddDialog: Boolean = false,
    val addForm: AddLeadForm = AddLeadForm(),
)

data class AddLeadForm(
    val type: LeadType = LeadType.MANUAL,
    val platform: String = "",
    val matchedValue: String = "",
    val textSnippet: String = "",
    val locationName: String = "",
    val lat: String = "",
    val lon: String = "",
    val confidence: String = "",
)

@HiltViewModel
class LeadsListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getLeads: GetLeadsByCaseUseCase,
    private val addLead: AddLeadUseCase,
    private val updateLeadStatus: UpdateLeadStatusUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(LeadsListState())
    val state: StateFlow<LeadsListState> = _state

    init {
        viewModelScope.launch {
            getLeads(caseId).collect { list ->
                _state.update { it.copy(leads = list, loading = false) }
            }
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, addForm = AddLeadForm()) }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun onTypeChange(type: LeadType) {
        _state.update { it.copy(addForm = it.addForm.copy(type = type)) }
    }

    fun onPlatformChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(platform = v)) }
    }

    fun onMatchedValueChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(matchedValue = v)) }
    }

    fun onTextSnippetChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(textSnippet = v)) }
    }

    fun onLocationNameChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(locationName = v)) }
    }

    fun onLatChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(lat = v)) }
    }

    fun onLonChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(lon = v)) }
    }

    fun onConfidenceChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(confidence = v)) }
    }

    fun submitLead() {
        val f = _state.value.addForm
        viewModelScope.launch {
            val lead = SearchLead(
                caseId = caseId,
                type = f.type,
                platform = f.platform.trimOrNull(),
                matchedValue = f.matchedValue.trimOrNull(),
                textSnippet = f.textSnippet.trimOrNull(),
                possibleLocationName = f.locationName.trimOrNull(),
                lat = f.lat.toDoubleOrNull(),
                lon = f.lon.toDoubleOrNull(),
                timestamp = System.currentTimeMillis(),
                confidence = f.confidence.toFloatOrNull() ?: 0f,
            )
            val id = addLead(lead)
            logAction("LEAD_ADDED", caseId = caseId, details = "Lead #$id type=${f.type}")
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun confirmLead(leadId: Long) {
        viewModelScope.launch {
            updateLeadStatus(leadId, LeadStatus.CONFIRMED)
            logAction("LEAD_CONFIRMED", caseId = caseId, details = "Lead #$leadId confirmed")
        }
    }

    fun rejectLead(leadId: Long) {
        viewModelScope.launch {
            updateLeadStatus(leadId, LeadStatus.REJECTED)
            logAction("LEAD_REJECTED", caseId = caseId, details = "Lead #$leadId rejected")
        }
    }

    private fun String.trimOrNull(): String? = trim().ifBlank { null }
}
