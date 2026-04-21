package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.WitnessReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WitnessReportDao {
    @Query("SELECT * FROM witness_reports WHERE caseId = :caseId ORDER BY timestamp DESC")
    fun observeByCase(caseId: Long): Flow<List<WitnessReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: WitnessReportEntity): Long

    @Update
    suspend fun update(report: WitnessReportEntity)
}
