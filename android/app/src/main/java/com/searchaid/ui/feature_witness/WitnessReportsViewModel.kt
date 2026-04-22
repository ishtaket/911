package com.searchaid.ui.feature_witness

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.usecase.AddWitnessReportUseCase
import com.searchaid.domain.usecase.GetWitnessReportsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdateWitnessReportStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WitnessReportsState(
    val reports: List<WitnessReport> = emptyList(),
    val loading: Boolean = true,
    val showAddDialog: Boolean = false,
    val addForm: AddReportForm = AddReportForm(),
)

data class AddReportForm(
    val sourceName: String = "",
    val sourceType: String = "",
    val text: String = "",
    val locationName: String = "",
    val lat: String = "",
    val lon: String = "",
    val confidence: String = "",
)

@HiltViewModel
class WitnessReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getReports: GetWitnessReportsByCaseUseCase,
    private val addReport: AddWitnessReportUseCase,
    private val updateReportStatus: UpdateWitnessReportStatusUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    val caseId: Long = savedStateHandle["caseId"] ?: -1L

    private val _state = MutableStateFlow(WitnessReportsState())
    val state: StateFlow<WitnessReportsState> = _state

    init {
        viewModelScope.launch {
            getReports(caseId).collect { list ->
                _state.update { it.copy(reports = list, loading = false) }
            }
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, addForm = AddReportForm()) }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialog = false) }
    }

    fun onSourceNameChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(sourceName = v)) }
    }

    fun onSourceTypeChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(sourceType = v)) }
    }

    fun onTextChange(v: String) {
        _state.update { it.copy(addForm = it.addForm.copy(text = v)) }
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

    fun submitReport() {
        val f = _state.value.addForm
        if (f.text.isBlank()) return
        viewModelScope.launch {
            val report = WitnessReport(
                caseId = caseId,
                sourceName = f.sourceName.trimOrNull(),
                sourceType = f.sourceType.trimOrNull(),
                text = f.text.trim(),
                timestamp = System.currentTimeMillis(),
                possibleLocationName = f.locationName.trimOrNull(),
                lat = f.lat.toDoubleOrNull(),
                lon = f.lon.toDoubleOrNull(),
                confidence = f.confidence.toFloatOrNull() ?: 0f,
            )
            val id = addReport(report)
            logAction("REPORT_ADDED", caseId = caseId, details = "Report #$id")
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun verifyReport(reportId: Long) {
        viewModelScope.launch {
            updateReportStatus(reportId, ReportStatus.VERIFIED)
            logAction("REPORT_VERIFIED", caseId = caseId, details = "Report #$reportId verified")
        }
    }

    fun rejectReport(reportId: Long) {
        viewModelScope.launch {
            updateReportStatus(reportId, ReportStatus.REJECTED)
            logAction("REPORT_REJECTED", caseId = caseId, details = "Report #$reportId rejected")
        }
    }

    private fun String.trimOrNull(): String? = trim().ifBlank { null }
}
