package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.PersonProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonProfileDao {
    @Query("SELECT * FROM person_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PersonProfileEntity>>

    @Query("SELECT * FROM person_profiles WHERE id = :id")
    suspend fun getById(id: Long): PersonProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PersonProfileEntity): Long

    @Update
    suspend fun update(profile: PersonProfileEntity)

    @Query("DELETE FROM person_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
