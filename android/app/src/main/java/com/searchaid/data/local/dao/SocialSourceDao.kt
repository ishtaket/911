package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.SocialSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialSourceDao {
    @Query("SELECT * FROM social_sources WHERE personId = :personId ORDER BY platform ASC")
    fun observeByPerson(personId: Long): Flow<List<SocialSourceEntity>>

    @Query("SELECT * FROM social_sources WHERE id = :id")
    suspend fun getById(id: Long): SocialSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SocialSourceEntity): Long

    @Update
    suspend fun update(source: SocialSourceEntity)

    @Query("DELETE FROM social_sources WHERE id = :id")
    suspend fun deleteById(id: Long)
}
