package com.searchaid.ui.feature_map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.usecase.GetLeadsByCaseUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetSearchZonesByCaseUseCase
import com.searchaid.domain.usecase.GetWitnessReportsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.MarkZoneCheckedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchMapState(
    val case_: MissingCase? = null,
    val zones: List<SearchZone> = emptyList(),
    val leads: List<SearchLead> = emptyList(),
    val reports: List<WitnessReport> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class SearchMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCase: GetMissingCaseUseCase,
    private val getZones: GetSearchZonesByCaseUseCase,
    private val getLeads: GetLeadsByCaseUseCase,
    private val getReports: GetWitnessReportsByCaseUseCase,
    private val markZoneChecked: MarkZoneCheckedUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(SearchMapState())
    val state: StateFlow<SearchMapState> = _state

    init {
        viewModelScope.launch {
            val case = getCase(caseId)
            _state.update { it.copy(case_ = case) }
        }
        viewModelScope.launch {
            getZones(caseId).collect { zones ->
                _state.update { it.copy(zones = zones, loading = false) }
            }
        }
        viewModelScope.launch {
            getLeads(caseId).collect { leads ->
                _state.update { it.copy(leads = leads) }
            }
        }
        viewModelScope.launch {
            getReports(caseId).collect { reports ->
                _state.update { it.copy(reports = reports) }
            }
        }
    }

    fun onZoneChecked(zoneId: Long) {
        viewModelScope.launch {
            markZoneChecked(zoneId)
            logAction("ZONE_CHECKED", caseId = caseId, details = "Zone #$zoneId marked checked")
        }
    }
}
