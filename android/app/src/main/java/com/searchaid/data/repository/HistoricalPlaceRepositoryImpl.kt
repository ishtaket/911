package com.searchaid.data.repository

import com.searchaid.data.local.dao.HistoricalPlaceDao
import com.searchaid.data.local.entity.HistoricalPlaceEntity
import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.repository.HistoricalPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoricalPlaceRepositoryImpl @Inject constructor(
    private val dao: HistoricalPlaceDao,
) : HistoricalPlaceRepository {

    override fun observeByPerson(personId: Long): Flow<List<HistoricalPlace>> =
        dao.observeByPerson(personId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(place: HistoricalPlace): Long =
        dao.insert(HistoricalPlaceEntity.fromDomain(place))

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
