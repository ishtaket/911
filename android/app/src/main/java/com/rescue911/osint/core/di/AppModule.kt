package com.rescue911.osint.core.di

import com.rescue911.osint.data.repository.MockRescue911Repository
import com.rescue911.osint.data.repository.Rescue911Repository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindRepository(impl: MockRescue911Repository): Rescue911Repository
}
