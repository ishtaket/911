package com.searchaid.domain.usecase

import com.searchaid.domain.model.SyncStatus
import com.searchaid.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSyncStatusUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(): Flow<SyncStatus> = syncRepository.syncStatus
}
