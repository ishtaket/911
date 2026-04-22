package com.searchaid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.searchaid.data.local.converter.Converters
import com.searchaid.data.local.dao.AuditLogDao
import com.searchaid.data.local.dao.HistoricalPlaceDao
import com.searchaid.data.local.dao.OutreachMessageDao
import com.searchaid.data.local.dao.MissingCaseDao
import com.searchaid.data.local.dao.PersonProfileDao
import com.searchaid.data.local.dao.SearchLeadDao
import com.searchaid.data.local.dao.SearchZoneDao
import com.searchaid.data.local.dao.SocialSourceDao
import com.searchaid.data.local.dao.WitnessReportDao
import com.searchaid.data.local.entity.AuditLogEntryEntity
import com.searchaid.data.local.entity.HistoricalPlaceEntity
import com.searchaid.data.local.entity.OutreachMessageEntity
import com.searchaid.data.local.entity.MissingCaseEntity
import com.searchaid.data.local.entity.PersonProfileEntity
import com.searchaid.data.local.entity.SearchLeadEntity
import com.searchaid.data.local.entity.SearchZoneEntity
import com.searchaid.data.local.entity.SocialSourceEntity
import com.searchaid.data.local.entity.WitnessReportEntity

@Database(
    entities = [
        PersonProfileEntity::class,
        MissingCaseEntity::class,
        HistoricalPlaceEntity::class,
        SearchLeadEntity::class,
        WitnessReportEntity::class,
        SearchZoneEntity::class,
        SocialSourceEntity::class,
        OutreachMessageEntity::class,
        AuditLogEntryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SearchAidDatabase : RoomDatabase() {
    abstract fun personProfileDao(): PersonProfileDao
    abstract fun missingCaseDao(): MissingCaseDao
    abstract fun historicalPlaceDao(): HistoricalPlaceDao
    abstract fun searchLeadDao(): SearchLeadDao
    abstract fun witnessReportDao(): WitnessReportDao
    abstract fun searchZoneDao(): SearchZoneDao
    abstract fun socialSourceDao(): SocialSourceDao
    abstract fun outreachMessageDao(): OutreachMessageDao
    abstract fun auditLogDao(): AuditLogDao
}
