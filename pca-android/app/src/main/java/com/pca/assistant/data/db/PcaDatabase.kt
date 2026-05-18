package com.pca.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pca.assistant.data.db.dao.DaySummaryDao
import com.pca.assistant.data.db.dao.HourSummaryDao
import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.LlmHealthDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.PlaceDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.DaySummaryEntity
import com.pca.assistant.data.db.entity.HourSummaryEntity
import com.pca.assistant.data.db.entity.InterventionEntity
import com.pca.assistant.data.db.entity.LlmHealthEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.data.db.entity.OwnerProfileEntity
import com.pca.assistant.data.db.entity.PlaceEntity
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.data.db.entity.WindowEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        OwnerProfileEntity::class,
        TranscriptEntity::class,
        WindowEntity::class,
        InterventionEntity::class,
        OpenThreadEntity::class,
        HourSummaryEntity::class,
        DaySummaryEntity::class,
        PlaceEntity::class,
        LlmHealthEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PcaDatabase : RoomDatabase() {
    abstract fun ownerDao(): OwnerDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun windowDao(): WindowDao
    abstract fun interventionDao(): InterventionDao
    abstract fun openThreadDao(): OpenThreadDao
    abstract fun hourSummaryDao(): HourSummaryDao
    abstract fun daySummaryDao(): DaySummaryDao
    abstract fun placeDao(): PlaceDao
    abstract fun llmHealthDao(): LlmHealthDao

    companion object {
        const val DB_NAME = "pca.db"

        fun build(context: Context, passphrase: ByteArray): PcaDatabase {
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, PcaDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
