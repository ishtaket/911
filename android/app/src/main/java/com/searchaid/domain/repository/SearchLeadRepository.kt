package com.searchaid.domain.repository

import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.SearchLead
import kotlinx.coroutines.flow.Flow

interface SearchLeadRepository {
    fun observeByCase(caseId: Long): Flow<List<SearchLead>>
    suspend fun add(lead: SearchLead): Long
    suspend fun updateStatus(id: Long, status: LeadStatus)
}
