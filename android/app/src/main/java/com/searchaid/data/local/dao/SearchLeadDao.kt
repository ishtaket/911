package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.SearchLeadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchLeadDao {
    @Query("SELECT * FROM search_leads WHERE caseId = :caseId ORDER BY confidence DESC")
    fun observeByCase(caseId: Long): Flow<List<SearchLeadEntity>>

    @Query("SELECT * FROM search_leads WHERE id = :id")
    suspend fun getById(id: Long): SearchLeadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lead: SearchLeadEntity): Long

    @Update
    suspend fun update(lead: SearchLeadEntity)
}
