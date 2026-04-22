package com.searchaid.data.repository

import com.searchaid.data.local.dao.OutreachMessageDao
import com.searchaid.data.local.entity.OutreachMessageEntity
import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus
import com.searchaid.domain.repository.OutreachMessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutreachMessageRepositoryImpl @Inject constructor(
    private val dao: OutreachMessageDao,
) : OutreachMessageRepository {

    override fun observeByCase(caseId: Long): Flow<List<OutreachMessage>> =
        dao.observeByCase(caseId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(message: OutreachMessage): Long =
        dao.insert(OutreachMessageEntity.fromDomain(message))

    override suspend fun updateStatus(id: Long, status: OutreachStatus) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = status.name, sentAt = if (status == OutreachStatus.SENT) System.currentTimeMillis() else existing.sentAt))
    }
}
