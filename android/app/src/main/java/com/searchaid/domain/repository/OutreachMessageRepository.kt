package com.searchaid.domain.repository

import com.searchaid.domain.model.OutreachMessage
import com.searchaid.domain.model.OutreachStatus
import kotlinx.coroutines.flow.Flow

interface OutreachMessageRepository {
    fun observeByCase(caseId: Long): Flow<List<OutreachMessage>>
    suspend fun add(message: OutreachMessage): Long
    suspend fun updateStatus(id: Long, status: OutreachStatus)
}
