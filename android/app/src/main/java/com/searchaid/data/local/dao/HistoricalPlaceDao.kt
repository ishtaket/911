package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.searchaid.data.local.entity.HistoricalPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoricalPlaceDao {
    @Query("SELECT * FROM historical_places WHERE personId = :personId")
    fun observeByPerson(personId: Long): Flow<List<HistoricalPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: HistoricalPlaceEntity): Long

    @Query("DELETE FROM historical_places WHERE id = :id")
    suspend fun deleteById(id: Long)
}
