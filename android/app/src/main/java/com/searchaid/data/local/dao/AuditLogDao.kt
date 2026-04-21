package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.searchaid.data.local.entity.AuditLogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log WHERE caseId = :caseId ORDER BY timestamp DESC")
    fun observeByCase(caseId: Long): Flow<List<AuditLogEntryEntity>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AuditLogEntryEntity>>

    @Insert
    suspend fun insert(entry: AuditLogEntryEntity): Long
}
