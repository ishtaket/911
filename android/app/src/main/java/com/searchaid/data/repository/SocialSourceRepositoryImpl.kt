package com.searchaid.data.repository

import com.searchaid.data.local.dao.SocialSourceDao
import com.searchaid.data.local.entity.SocialSourceEntity
import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.repository.SocialSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialSourceRepositoryImpl @Inject constructor(
    private val dao: SocialSourceDao,
) : SocialSourceRepository {

    override fun observeByPerson(personId: Long): Flow<List<SocialSource>> =
        dao.observeByPerson(personId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(source: SocialSource): Long =
        dao.insert(SocialSourceEntity.fromDomain(source))

    override suspend fun update(source: SocialSource) =
        dao.update(SocialSourceEntity.fromDomain(source))

    override suspend fun delete(id: Long) =
        dao.deleteById(id)
}
