package com.searchaid.domain.repository

import com.searchaid.domain.model.PersonProfile
import kotlinx.coroutines.flow.Flow

interface PersonProfileRepository {
    fun observeAll(): Flow<List<PersonProfile>>
    suspend fun getById(id: Long): PersonProfile?
    suspend fun create(profile: PersonProfile): Long
    suspend fun update(profile: PersonProfile)
    suspend fun delete(id: Long)
}
