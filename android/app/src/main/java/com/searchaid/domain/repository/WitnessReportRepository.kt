package com.searchaid.domain.repository

import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import kotlinx.coroutines.flow.Flow

interface WitnessReportRepository {
    fun observeByCase(caseId: Long): Flow<List<WitnessReport>>
    suspend fun add(report: WitnessReport): Long
    suspend fun updateStatus(id: Long, status: ReportStatus)
}
