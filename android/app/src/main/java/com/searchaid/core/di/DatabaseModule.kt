package com.searchaid.core.di

import android.content.Context
import androidx.room.Room
import com.searchaid.core.security.DatabaseKeyManager
import com.searchaid.data.local.SearchAidDatabase
import com.searchaid.data.local.dao.AuditLogDao
import com.searchaid.data.local.dao.HistoricalPlaceDao
import com.searchaid.data.local.dao.MissingCaseDao
import com.searchaid.data.local.dao.OutreachMessageDao
import com.searchaid.data.local.dao.PersonProfileDao
import com.searchaid.data.local.dao.SearchLeadDao
import com.searchaid.data.local.dao.SearchZoneDao
import com.searchaid.data.local.dao.SocialSourceDao
import com.searchaid.data.local.dao.WitnessReportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SearchAidDatabase {
        val passphrase = DatabaseKeyManager.getPassphrase(context)
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(context, SearchAidDatabase::class.java, "searchaid.db")
            .openHelperFactory(factory)
            .addMigrations(
                SearchAidDatabase.MIGRATION_1_2,
                SearchAidDatabase.MIGRATION_2_3,
            )
            .build()
    }

    @Provides fun providePersonProfileDao(db: SearchAidDatabase): PersonProfileDao = db.personProfileDao()
    @Provides fun provideMissingCaseDao(db: SearchAidDatabase): MissingCaseDao = db.missingCaseDao()
    @Provides fun provideHistoricalPlaceDao(db: SearchAidDatabase): HistoricalPlaceDao = db.historicalPlaceDao()
    @Provides fun provideSearchLeadDao(db: SearchAidDatabase): SearchLeadDao = db.searchLeadDao()
    @Provides fun provideWitnessReportDao(db: SearchAidDatabase): WitnessReportDao = db.witnessReportDao()
    @Provides fun provideSearchZoneDao(db: SearchAidDatabase): SearchZoneDao = db.searchZoneDao()
    @Provides fun provideSocialSourceDao(db: SearchAidDatabase): SocialSourceDao = db.socialSourceDao()
    @Provides fun provideOutreachMessageDao(db: SearchAidDatabase): OutreachMessageDao = db.outreachMessageDao()
    @Provides fun provideAuditLogDao(db: SearchAidDatabase): AuditLogDao = db.auditLogDao()
}
