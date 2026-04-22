package com.searchaid.domain.usecase

import com.searchaid.domain.model.AuditLogEntry
import com.searchaid.domain.repository.AuditLogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuditUseCasesTest {

    private val repository = mockk<AuditLogRepository>(relaxed = true)

    @Test
    fun `LogAction creates entry with correct fields`() = runTest {
        val entrySlot = slot<AuditLogEntry>()
        coEvery { repository.log(capture(entrySlot)) } returns 1L
        val useCase = LogActionUseCase(repository)

        useCase("CASE_STARTED", caseId = 5, details = "Started search")

        val captured = entrySlot.captured
        assertEquals("CASE_STARTED", captured.action)
        assertEquals(5L, captured.caseId)
        assertEquals("Started search", captured.details)
    }

    @Test
    fun `LogAction with no caseId sets null`() = runTest {
        val entrySlot = slot<AuditLogEntry>()
        coEvery { repository.log(capture(entrySlot)) } returns 1L
        val useCase = LogActionUseCase(repository)

        useCase("PROFILE_CREATED", details = "Created profile: Test")

        assertNull(entrySlot.captured.caseId)
    }

    @Test
    fun `GetAuditLog byCase returns filtered entries`() = runTest {
        val entries = listOf(
            AuditLogEntry(1, 5, "CASE_STARTED", "details", null, 1000),
        )
        coEvery { repository.observeByCase(5) } returns flowOf(entries)
        val useCase = GetAuditLogUseCase(repository)

        val result = useCase.byCase(5).first()

        assertEquals(1, result.size)
        assertEquals("CASE_STARTED", result[0].action)
    }

    @Test
    fun `GetAuditLog all returns all entries`() = runTest {
        val entries = listOf(
            AuditLogEntry(1, null, "PROFILE_CREATED", null, null, 1000),
            AuditLogEntry(2, 5, "CASE_STARTED", null, null, 2000),
        )
        coEvery { repository.observeAll() } returns flowOf(entries)
        val useCase = GetAuditLogUseCase(repository)

        val result = useCase.all().first()

        assertEquals(2, result.size)
    }
}
