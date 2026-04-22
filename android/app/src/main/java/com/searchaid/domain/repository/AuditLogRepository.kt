package com.searchaid.domain.repository

import com.searchaid.domain.model.AuditLogEntry
import kotlinx.coroutines.flow.Flow

interface AuditLogRepository {
    fun observeByCase(caseId: Long): Flow<List<AuditLogEntry>>
    fun observeAll(): Flow<List<AuditLogEntry>>
    suspend fun log(entry: AuditLogEntry): Long
}
