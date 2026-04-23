package com.searchaid.domain.repository

import com.searchaid.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for cloud sync (Firestore). Offline mode is a no-op;
 * when Firebase is configured, swap to FirestoreSyncRepository via DI.
 */
interface SyncRepository {
    val syncStatus: Flow<SyncStatus>
    suspend fun pushCase(caseId: Long)
    suspend fun pullCase(caseId: Long)
    suspend fun syncAll()
    suspend fun markDirty(caseId: Long)
}
