package com.searchaid.domain.repository

import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import kotlinx.coroutines.flow.Flow

interface MissingCaseRepository {
    fun observeActive(): Flow<List<MissingCase>>
    fun observeByPerson(personId: Long): Flow<List<MissingCase>>
    suspend fun getById(id: Long): MissingCase?
    suspend fun create(case_: MissingCase): Long
    suspend fun updateStatus(id: Long, status: CaseStatus)
}
