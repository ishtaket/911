package com.searchaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.searchaid.data.local.entity.MissingCaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissingCaseDao {
    @Query("SELECT * FROM missing_cases WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<MissingCaseEntity>>

    @Query("SELECT * FROM missing_cases WHERE id = :id")
    suspend fun getById(id: Long): MissingCaseEntity?

    @Query("SELECT * FROM missing_cases WHERE personId = :personId ORDER BY createdAt DESC")
    fun observeByPerson(personId: Long): Flow<List<MissingCaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(case_: MissingCaseEntity): Long

    @Update
    suspend fun update(case_: MissingCaseEntity)
}
