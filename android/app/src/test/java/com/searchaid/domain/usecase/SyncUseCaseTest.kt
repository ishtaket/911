package com.searchaid.domain.usecase

import com.searchaid.data.repository.OfflineSyncRepository
import com.searchaid.domain.model.SyncState
import com.searchaid.domain.repository.SyncRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncUseCaseTest {

    private val offlineSync = OfflineSyncRepository()

    @Test
    fun `GetSyncStatus returns OFFLINE in offline mode`() = runTest {
        val useCase = GetSyncStatusUseCase(offlineSync)
        val status = useCase().first()
        assertEquals(SyncState.OFFLINE, status.state)
        assertEquals(0, status.pendingChanges)
    }

    @Test
    fun `SyncCase push is no-op in offline mode`() = runTest {
        val useCase = SyncCaseUseCase(offlineSync)
        useCase.push(1L) // Should not throw
        val status = offlineSync.syncStatus.first()
        assertEquals(SyncState.OFFLINE, status.state)
    }

    @Test
    fun `SyncCase pull is no-op in offline mode`() = runTest {
        val useCase = SyncCaseUseCase(offlineSync)
        useCase.pull(1L) // Should not throw
    }

    @Test
    fun `SyncCase syncAll is no-op in offline mode`() = runTest {
        val useCase = SyncCaseUseCase(offlineSync)
        useCase.syncAll() // Should not throw
    }

    @Test
    fun `markDirty increments pending changes`() = runTest {
        offlineSync.markDirty(1L)
        offlineSync.markDirty(2L)
        val status = offlineSync.syncStatus.first()
        assertEquals(2, status.pendingChanges)
    }

    @Test
    fun `SyncCase delegates to repository`() = runTest {
        val mockSync = mockk<SyncRepository>(relaxed = true)
        val useCase = SyncCaseUseCase(mockSync)
        useCase.push(5L)
        useCase.pull(5L)
        useCase.syncAll()
        coVerify { mockSync.pushCase(5L) }
        coVerify { mockSync.pullCase(5L) }
        coVerify { mockSync.syncAll() }
    }
}
