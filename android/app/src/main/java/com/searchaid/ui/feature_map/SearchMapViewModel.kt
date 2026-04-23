package com.searchaid.ui.feature_map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.signal.HeatMapDataBuilder
import com.searchaid.domain.signal.HeatMapPoint
import com.searchaid.domain.signal.ScoredZone
import com.searchaid.domain.usecase.AggregateSignalsUseCase
import com.searchaid.domain.usecase.GetHistoricalPlacesUseCase
import com.searchaid.domain.usecase.GetLeadsByCaseUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetSearchZonesByCaseUseCase
import com.searchaid.domain.usecase.GetWitnessReportsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.MarkZoneCheckedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchMapState(
    val case_: MissingCase? = null,
    val zones: List<SearchZone> = emptyList(),
    val suggestedZones: List<ScoredZone> = emptyList(),
    val leads: List<SearchLead> = emptyList(),
    val reports: List<WitnessReport> = emptyList(),
    val heatMapEnabled: Boolean = false,
    val heatMapPoints: List<HeatMapPoint> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class SearchMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCase: GetMissingCaseUseCase,
    private val getZones: GetSearchZonesByCaseUseCase,
    private val getLeads: GetLeadsByCaseUseCase,
    private val getReports: GetWitnessReportsByCaseUseCase,
    private val getHistoricalPlaces: GetHistoricalPlacesUseCase,
    private val aggregateSignals: AggregateSignalsUseCase,
    private val markZoneChecked: MarkZoneCheckedUseCase,
    private val logAction: LogActionUseCase,
    private val heatMapDataBuilder: HeatMapDataBuilder,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(SearchMapState())
    val state: StateFlow<SearchMapState> = _state

    init {
        viewModelScope.launch {
            val case_ = getCase(caseId)
            _state.update { it.copy(case_ = case_) }

            if (case_ != null) {
                // Combine reactive data streams to recompute suggested zones
                combine(
                    getLeads(caseId),
                    getReports(caseId),
                    getHistoricalPlaces(case_.personId),
                ) { leads, reports, places ->
                    Triple(leads, reports, places)
                }.collect { (leads, reports, places) ->
                    val suggested = aggregateSignals(case_, leads, reports, places)
                    val allSignals = suggested.flatMap { it.signals }
                    val heatPoints = heatMapDataBuilder.build(allSignals)
                    _state.update {
                        it.copy(
                            leads = leads,
                            reports = reports,
                            suggestedZones = suggested,
                            heatMapPoints = heatPoints,
                            loading = false,
                        )
                    }
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
        viewModelScope.launch {
            getZones(caseId).collect { zones ->
                _state.update { it.copy(zones = zones) }
            }
        }
    }

    fun onToggleHeatMap() {
        _state.update { it.copy(heatMapEnabled = !it.heatMapEnabled) }
    }

    fun onZoneChecked(zoneId: Long) {
        viewModelScope.launch {
            markZoneChecked(zoneId)
            logAction("ZONE_CHECKED", caseId = caseId, details = "Zone #$zoneId marked checked")
        }
    }
}
