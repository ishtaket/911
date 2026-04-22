package com.searchaid.core.di

import com.searchaid.data.repository.AuditLogRepositoryImpl
import com.searchaid.data.repository.HistoricalPlaceRepositoryImpl
import com.searchaid.data.repository.MissingCaseRepositoryImpl
import com.searchaid.data.repository.PersonProfileRepositoryImpl
import com.searchaid.data.repository.SearchLeadRepositoryImpl
import com.searchaid.data.repository.OutreachMessageRepositoryImpl
import com.searchaid.data.repository.SearchZoneRepositoryImpl
import com.searchaid.data.repository.SocialSourceRepositoryImpl
import com.searchaid.data.repository.WitnessReportRepositoryImpl
import com.searchaid.domain.repository.AuditLogRepository
import com.searchaid.domain.repository.HistoricalPlaceRepository
import com.searchaid.domain.repository.MissingCaseRepository
import com.searchaid.domain.repository.PersonProfileRepository
import com.searchaid.domain.repository.SearchLeadRepository
import com.searchaid.domain.repository.OutreachMessageRepository
import com.searchaid.domain.repository.SearchZoneRepository
import com.searchaid.domain.repository.SocialSourceRepository
import com.searchaid.domain.repository.WitnessReportRepository
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

    @Binds
    @Singleton
    abstract fun bindSearchLeadRepository(
        impl: SearchLeadRepositoryImpl,
    ): SearchLeadRepository

    @Binds
    @Singleton
    abstract fun bindWitnessReportRepository(
        impl: WitnessReportRepositoryImpl,
    ): WitnessReportRepository

    @Binds
    @Singleton
    abstract fun bindSearchZoneRepository(
        impl: SearchZoneRepositoryImpl,
    ): SearchZoneRepository

    @Binds
    @Singleton
    abstract fun bindSocialSourceRepository(
        impl: SocialSourceRepositoryImpl,
    ): SocialSourceRepository

    @Binds
    @Singleton
    abstract fun bindOutreachMessageRepository(
        impl: OutreachMessageRepositoryImpl,
    ): OutreachMessageRepository
}
