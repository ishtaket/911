package com.searchaid.domain.repository

import com.searchaid.domain.model.HistoricalPlace
import kotlinx.coroutines.flow.Flow

interface HistoricalPlaceRepository {
    fun observeByPerson(personId: Long): Flow<List<HistoricalPlace>>
    suspend fun add(place: HistoricalPlace): Long
    suspend fun delete(id: Long)
}
