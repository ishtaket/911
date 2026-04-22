package com.searchaid.domain.repository

import com.searchaid.domain.model.SearchZone
import kotlinx.coroutines.flow.Flow

interface SearchZoneRepository {
    fun observeByCase(caseId: Long): Flow<List<SearchZone>>
    suspend fun add(zone: SearchZone): Long
    suspend fun markChecked(id: Long)
}
