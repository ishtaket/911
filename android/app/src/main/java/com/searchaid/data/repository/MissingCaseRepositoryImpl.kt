package com.searchaid.data.repository

import com.searchaid.data.local.dao.MissingCaseDao
import com.searchaid.data.local.entity.MissingCaseEntity
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.repository.MissingCaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MissingCaseRepositoryImpl @Inject constructor(
    private val dao: MissingCaseDao,
) : MissingCaseRepository {

    override fun observeActive(): Flow<List<MissingCase>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeByPerson(personId: Long): Flow<List<MissingCase>> =
        dao.observeByPerson(personId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): MissingCase? =
        dao.getById(id)?.toDomain()

    override suspend fun create(case_: MissingCase): Long =
        dao.insert(MissingCaseEntity.fromDomain(case_))

    override suspend fun updateStatus(id: Long, status: CaseStatus) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = status.name))
    }
}
