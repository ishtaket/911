package com.searchaid.data.repository

import com.searchaid.data.local.dao.AuditLogDao
import com.searchaid.data.local.entity.AuditLogEntryEntity
import com.searchaid.domain.model.AuditLogEntry
import com.searchaid.domain.repository.AuditLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val dao: AuditLogDao,
) : AuditLogRepository {

    override fun observeByCase(caseId: Long): Flow<List<AuditLogEntry>> =
        dao.observeByCase(caseId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<AuditLogEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun log(entry: AuditLogEntry): Long =
        dao.insert(AuditLogEntryEntity.fromDomain(entry))
}
