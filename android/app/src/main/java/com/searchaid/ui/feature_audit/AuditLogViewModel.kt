package com.searchaid.ui.feature_audit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.AuditLogEntry
import com.searchaid.domain.usecase.GetAuditLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getAuditLog: GetAuditLogUseCase,
) : ViewModel() {

    private val caseId: Long = savedStateHandle["caseId"] ?: -1L

    val entries: StateFlow<List<AuditLogEntry>> =
        (if (caseId > 0) getAuditLog.byCase(caseId) else getAuditLog.all())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isFiltered: Boolean = caseId > 0
}
