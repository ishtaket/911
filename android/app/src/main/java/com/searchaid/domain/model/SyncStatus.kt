package com.searchaid.domain.model

enum class SyncState {
    IDLE,
    SYNCING,
    ERROR,
    OFFLINE,
}

data class SyncStatus(
    val state: SyncState = SyncState.OFFLINE,
    val lastSyncedAt: Long? = null,
    val pendingChanges: Int = 0,
    val errorMessage: String? = null,
)
