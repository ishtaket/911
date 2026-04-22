package com.searchaid.core.di

import com.searchaid.data.repository.AuditLogRepositoryImpl
import com.searchaid.data.repository.HistoricalPlaceRepositoryImpl
import com.searchaid.data.repository.MissingCaseRepositoryImpl
import com.searchaid.data.repository.PersonProfileRepositoryImpl
import com.searchaid.domain.repository.AuditLogRepository
import com.searchaid.domain.repository.HistoricalPlaceRepository
import com.searchaid.domain.repository.MissingCaseRepository
import com.searchaid.domain.repository.PersonProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPersonProfileRepository(
        impl: PersonProfileRepositoryImpl,
    ): PersonProfileRepository

    @Binds
    @Singleton
    abstract fun bindHistoricalPlaceRepository(
        impl: HistoricalPlaceRepositoryImpl,
    ): HistoricalPlaceRepository

    @Binds
    @Singleton
    abstract fun bindMissingCaseRepository(
        impl: MissingCaseRepositoryImpl,
    ): MissingCaseRepository

    @Binds
    @Singleton
    abstract fun bindAuditLogRepository(
        impl: AuditLogRepositoryImpl,
    ): AuditLogRepository
}
