package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.SearchZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchZoneDao {
    @Query("SELECT * FROM search_zones WHERE caseId = :caseId ORDER BY score DESC")
    fun observeByCase(caseId: Long): Flow<List<SearchZoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: SearchZoneEntity): Long

    @Update
    suspend fun update(zone: SearchZoneEntity)

    @Query("UPDATE search_zones SET checked = 1 WHERE id = :id")
    suspend fun markChecked(id: Long)
}
