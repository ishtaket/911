package com.searchaid.data.repository

import com.searchaid.data.local.dao.SearchLeadDao
import com.searchaid.data.local.entity.SearchLeadEntity
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.repository.SearchLeadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchLeadRepositoryImpl @Inject constructor(
    private val dao: SearchLeadDao,
) : SearchLeadRepository {

    override fun observeByCase(caseId: Long): Flow<List<SearchLead>> =
        dao.observeByCase(caseId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(lead: SearchLead): Long =
        dao.insert(SearchLeadEntity.fromDomain(lead))

    override suspend fun updateStatus(id: Long, status: LeadStatus) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = status.name))
    }
}
