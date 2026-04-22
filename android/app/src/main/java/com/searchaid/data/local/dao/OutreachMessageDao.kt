package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.OutreachMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutreachMessageDao {
    @Query("SELECT * FROM outreach_messages WHERE caseId = :caseId ORDER BY sentAt DESC")
    fun observeByCase(caseId: Long): Flow<List<OutreachMessageEntity>>

    @Query("SELECT * FROM outreach_messages WHERE id = :id")
    suspend fun getById(id: Long): OutreachMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutreachMessageEntity): Long

    @Update
    suspend fun update(message: OutreachMessageEntity)
}
