package com.searchaid.data.repository

import com.searchaid.data.local.dao.PersonProfileDao
import com.searchaid.data.local.entity.PersonProfileEntity
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.repository.PersonProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonProfileRepositoryImpl @Inject constructor(
    private val dao: PersonProfileDao,
) : PersonProfileRepository {

    override fun observeAll(): Flow<List<PersonProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): PersonProfile? =
        dao.getById(id)?.toDomain()

    override suspend fun create(profile: PersonProfile): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            PersonProfileEntity.fromDomain(profile.copy(createdAt = now, updatedAt = now))
        )
    }

    override suspend fun update(profile: PersonProfile) {
        dao.update(
            PersonProfileEntity.fromDomain(profile.copy(updatedAt = System.currentTimeMillis()))
        )
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
