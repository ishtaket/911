package com.searchaid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    exportSchema = true,
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

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `social_sources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `personId` INTEGER NOT NULL,
                        `platform` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `title` TEXT,
                        `handleOrAlias` TEXT,
                        `region` TEXT,
                        `url` TEXT,
                        `visibility` TEXT,
                        `enabled` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `outreach_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `caseId` INTEGER NOT NULL,
                        `channel` TEXT NOT NULL,
                        `recipient` TEXT NOT NULL,
                        `messageText` TEXT NOT NULL,
                        `sentAt` INTEGER,
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
