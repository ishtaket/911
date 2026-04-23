package com.searchaid.domain.usecase

import com.searchaid.domain.repository.SyncRepository
import javax.inject.Inject

class SyncCaseUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    suspend fun push(caseId: Long) = syncRepository.pushCase(caseId)
    suspend fun pull(caseId: Long) = syncRepository.pullCase(caseId)
    suspend fun syncAll() = syncRepository.syncAll()
}
