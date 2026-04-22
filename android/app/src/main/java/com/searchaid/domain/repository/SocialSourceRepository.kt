package com.searchaid.domain.repository

import com.searchaid.domain.model.SocialSource
import kotlinx.coroutines.flow.Flow

interface SocialSourceRepository {
    fun observeByPerson(personId: Long): Flow<List<SocialSource>>
    suspend fun add(source: SocialSource): Long
    suspend fun update(source: SocialSource)
    suspend fun delete(id: Long)
}
