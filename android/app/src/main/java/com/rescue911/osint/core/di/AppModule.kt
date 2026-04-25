package com.rescue911.osint.core.di

import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.data.repository.Rescue911RepositoryDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    /**
     * The repository the app sees is the dispatcher. It picks Mock vs
     * Backend per call based on user prefs and backend health.
     */
    @Binds
    @Singleton
    abstract fun bindRepository(impl: Rescue911RepositoryDispatcher): Rescue911Repository
}
