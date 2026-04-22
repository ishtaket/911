package com.searchaid.data.repository

import com.searchaid.data.local.dao.WitnessReportDao
import com.searchaid.data.local.entity.WitnessReportEntity
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.repository.WitnessReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WitnessReportRepositoryImpl @Inject constructor(
    private val dao: WitnessReportDao,
) : WitnessReportRepository {

    override fun observeByCase(caseId: Long): Flow<List<WitnessReport>> =
        dao.observeByCase(caseId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(report: WitnessReport): Long =
        dao.insert(WitnessReportEntity.fromDomain(report))

    override suspend fun updateStatus(id: Long, status: ReportStatus) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = status.name))
    }
}
