package com.pca.assistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pca.assistant.data.db.entity.DaySummaryEntity
import com.pca.assistant.data.db.entity.HourSummaryEntity
import com.pca.assistant.data.db.entity.InterventionEntity
import com.pca.assistant.data.db.entity.LlmHealthEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.data.db.entity.OwnerProfileEntity
import com.pca.assistant.data.db.entity.PlaceEntity
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.data.db.entity.WindowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnerDao {
    @Query("SELECT * FROM owner_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): OwnerProfileEntity?

    @Query("SELECT * FROM owner_profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<OwnerProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OwnerProfileEntity)

    @Query("DELETE FROM owner_profile")
    suspend fun wipe()
}

@Dao
interface TranscriptDao {
    @Insert
    suspend fun insert(entity: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE ts >= :fromTs AND ts < :toTs ORDER BY ts ASC")
    suspend fun between(fromTs: Long, toTs: Long): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE ts >= :sinceTs ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(sinceTs: Long, limit: Int = 200): Flow<List<TranscriptEntity>>

    @Query("DELETE FROM transcripts WHERE ts < :olderThan")
    suspend fun purgeOlderThan(olderThan: Long)

    @Query("DELETE FROM transcripts")
    suspend fun wipe()
}

@Dao
interface WindowDao {
    @Insert
    suspend fun insert(entity: WindowEntity): Long

    @Update
    suspend fun update(entity: WindowEntity)

    @Query("SELECT * FROM windows WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): WindowEntity?

    @Query("SELECT * FROM windows ORDER BY startTs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<WindowEntity>>

    /**
     * Half-open window slice `[fromTs, toTs)` ordered chronologically. Used
     * by [com.pca.assistant.pipeline.HourRollupWorker] to harvest
     * memory_notes for a specific hour (B-8 fix — the previous
     * `observeRecent(200).first()` could miss old hours on a busy day).
     */
    @Query("SELECT * FROM windows WHERE startTs >= :fromTs AND startTs < :toTs ORDER BY startTs ASC")
    suspend fun between(fromTs: Long, toTs: Long): List<WindowEntity>

    @Query("SELECT COUNT(*) FROM windows WHERE startTs >= :sinceTs")
    fun countSince(sinceTs: Long): Flow<Int>

    @Query("SELECT * FROM windows WHERE sentToLlm = 1 ORDER BY startTs DESC LIMIT 1")
    suspend fun lastSent(): WindowEntity?

    @Query("DELETE FROM windows WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM windows")
    suspend fun wipe()
}

@Dao
interface InterventionDao {
    @Insert
    suspend fun insert(entity: InterventionEntity): Long

    @Update
    suspend fun update(entity: InterventionEntity)

    @Query("SELECT * FROM interventions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): InterventionEntity?

    @Query("SELECT * FROM interventions ORDER BY ts DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<InterventionEntity>>

    @Query("SELECT COUNT(*) FROM interventions WHERE ts >= :sinceTs")
    fun countSince(sinceTs: Long): Flow<Int>

    @Query("SELECT * FROM interventions ORDER BY ts DESC LIMIT 1")
    suspend fun last(): InterventionEntity?

    @Query("DELETE FROM interventions")
    suspend fun wipe()
}

@Dao
interface OpenThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OpenThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OpenThreadEntity>)

    @Query("SELECT * FROM open_threads WHERE status = 'open' ORDER BY lastMentionedAt DESC")
    suspend fun openThreads(): List<OpenThreadEntity>

    @Query("SELECT * FROM open_threads WHERE status = 'open' ORDER BY lastMentionedAt DESC")
    fun observeOpen(): Flow<List<OpenThreadEntity>>

    @Query("UPDATE open_threads SET status = 'closed' WHERE id IN (:ids)")
    suspend fun close(ids: List<String>)

    @Query("DELETE FROM open_threads")
    suspend fun wipe()
}

@Dao
interface HourSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HourSummaryEntity)

    @Query("SELECT * FROM hour_summaries WHERE hourStart >= :fromTs AND hourStart < :toTs ORDER BY hourStart ASC")
    suspend fun between(fromTs: Long, toTs: Long): List<HourSummaryEntity>

    @Query("SELECT * FROM hour_summaries ORDER BY hourStart DESC LIMIT :limit")
    suspend fun recent(limit: Int = 24): List<HourSummaryEntity>

    @Query("DELETE FROM hour_summaries WHERE hourStart < :olderThan")
    suspend fun purgeOlderThan(olderThan: Long)

    @Query("DELETE FROM hour_summaries")
    suspend fun wipe()
}

@Dao
interface DaySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DaySummaryEntity)

    @Query("SELECT * FROM day_summaries ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<DaySummaryEntity>>

    @Query("SELECT * FROM day_summaries WHERE date = :date LIMIT 1")
    suspend fun byDate(date: String): DaySummaryEntity?

    @Query("DELETE FROM day_summaries WHERE date < :olderThan")
    suspend fun purgeOlderThan(olderThan: String)

    @Query("DELETE FROM day_summaries")
    suspend fun wipe()
}

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaceEntity): Long

    @Query("SELECT * FROM places")
    suspend fun all(): List<PlaceEntity>

    @Query("SELECT * FROM places")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM places")
    suspend fun wipe()
}

@Dao
interface LlmHealthDao {
    @Insert
    suspend fun insert(entity: LlmHealthEntity): Long

    @Query("SELECT * FROM llm_health WHERE provider = :provider ORDER BY ts DESC LIMIT :limit")
    suspend fun recentForProvider(provider: String, limit: Int = 10): List<LlmHealthEntity>

    @Query("DELETE FROM llm_health WHERE ts < :olderThan")
    suspend fun purgeOlderThan(olderThan: Long)
}
