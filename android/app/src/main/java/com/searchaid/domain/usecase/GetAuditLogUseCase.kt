package com.searchaid.domain.usecase

import com.searchaid.domain.model.AuditLogEntry
import com.searchaid.domain.repository.AuditLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAuditLogUseCase @Inject constructor(
    private val repository: AuditLogRepository,
) {
    fun byCase(caseId: Long): Flow<List<AuditLogEntry>> = repository.observeByCase(caseId)
    fun all(): Flow<List<AuditLogEntry>> = repository.observeAll()
}
