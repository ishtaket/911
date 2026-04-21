package com.searchaid.core.di

import com.searchaid.data.repository.HistoricalPlaceRepositoryImpl
import com.searchaid.data.repository.PersonProfileRepositoryImpl
import com.searchaid.domain.repository.HistoricalPlaceRepository
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
}
