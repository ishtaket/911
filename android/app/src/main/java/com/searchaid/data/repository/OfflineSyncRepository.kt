package com.searchaid.data.repository

import com.searchaid.domain.model.SyncState
import com.searchaid.domain.model.SyncStatus
import com.searchaid.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline sync stub. All data stays in Room only.
 * Replace with FirestoreSyncRepository when Firebase is configured.
 */
@Singleton
class OfflineSyncRepository @Inject constructor() : SyncRepository {

    private val _syncStatus = MutableStateFlow(SyncStatus(state = SyncState.OFFLINE))
    override val syncStatus: Flow<SyncStatus> = _syncStatus

    override suspend fun pushCase(caseId: Long) {
        // No-op in offline mode — data stays local
    }

    override suspend fun pullCase(caseId: Long) {
        // No-op in offline mode
    }

    override suspend fun syncAll() {
        // No-op in offline mode
    }

    override suspend fun markDirty(caseId: Long) {
        _syncStatus.value = _syncStatus.value.copy(
            pendingChanges = _syncStatus.value.pendingChanges + 1,
        )
    }
}
