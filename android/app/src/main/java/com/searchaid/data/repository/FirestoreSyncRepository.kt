package com.searchaid.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.searchaid.data.local.dao.MissingCaseDao
import com.searchaid.data.local.entity.MissingCaseEntity
import com.searchaid.domain.model.SyncState
import com.searchaid.domain.model.SyncStatus
import com.searchaid.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore sync implementation. To enable:
 * 1. Add google-services.json to app/
 * 2. In RepositoryModule, change bindSyncRepository to use this class
 */
@Singleton
class FirestoreSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val caseDao: MissingCaseDao,
) : SyncRepository {

    private val _syncStatus = MutableStateFlow(SyncStatus())
    override val syncStatus: Flow<SyncStatus> = _syncStatus

    override suspend fun pushCase(caseId: Long) {
        _syncStatus.update { it.copy(state = SyncState.SYNCING) }
        try {
            val case = caseDao.getById(caseId)
                ?: throw IllegalStateException("Case $caseId not found")
            firestore.collection("cases")
                .document(caseId.toString())
                .set(case.toMap())
                .await()
            _syncStatus.update {
                it.copy(
                    state = SyncState.IDLE,
                    lastSyncedAt = System.currentTimeMillis(),
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            _syncStatus.update {
                it.copy(state = SyncState.ERROR, errorMessage = "Push failed")
            }
        }
    }

    override suspend fun pullCase(caseId: Long) {
        _syncStatus.update { it.copy(state = SyncState.SYNCING) }
        try {
            val doc = firestore.collection("cases")
                .document(caseId.toString())
                .get()
                .await()
            if (doc.exists()) {
                _syncStatus.update {
                    it.copy(
                        state = SyncState.IDLE,
                        lastSyncedAt = System.currentTimeMillis(),
                        errorMessage = null,
                    )
                }
            }
        } catch (e: Exception) {
            _syncStatus.update {
                it.copy(state = SyncState.ERROR, errorMessage = "Pull failed")
            }
        }
    }

    override suspend fun syncAll() {
        _syncStatus.update { it.copy(state = SyncState.SYNCING) }
        try {
            _syncStatus.update {
                it.copy(
                    state = SyncState.IDLE,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingChanges = 0,
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            _syncStatus.update {
                it.copy(state = SyncState.ERROR, errorMessage = "Sync failed")
            }
        }
    }

    override suspend fun markDirty(caseId: Long) {
        _syncStatus.update { it.copy(pendingChanges = it.pendingChanges + 1) }
    }

    private fun MissingCaseEntity.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "personId" to personId,
        "status" to status,
        "createdAt" to createdAt,
        "lastSeenTime" to lastSeenTime,
        "lastSeenLocationName" to lastSeenLocationName,
        "lastSeenLat" to lastSeenLat,
        "lastSeenLon" to lastSeenLon,
        "clothesDescription" to clothesDescription,
        "notes" to notes,
        "operatorId" to operatorId,
    )
}
