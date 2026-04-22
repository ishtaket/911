package com.searchaid.data.repository

import com.searchaid.data.local.dao.SearchZoneDao
import com.searchaid.data.local.entity.SearchZoneEntity
import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.repository.SearchZoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchZoneRepositoryImpl @Inject constructor(
    private val dao: SearchZoneDao,
) : SearchZoneRepository {

    override fun observeByCase(caseId: Long): Flow<List<SearchZone>> =
        dao.observeByCase(caseId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(zone: SearchZone): Long =
        dao.insert(SearchZoneEntity.fromDomain(zone))

    override suspend fun markChecked(id: Long) =
        dao.markChecked(id)
}
