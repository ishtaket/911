package com.searchaid.domain.usecase

import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.repository.SearchLeadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LeadUseCasesTest {

    private val repository = mockk<SearchLeadRepository>(relaxed = true)

    private val testLead = SearchLead(
        id = 1,
        caseId = 10,
        type = LeadType.MANUAL,
        platform = "Telegram",
        matchedValue = "user123",
        textSnippet = "Seen near park",
        possibleLocationName = "Central Park",
        lat = 55.75,
        lon = 37.61,
        timestamp = 1000L,
        confidence = 0.8f,
        status = LeadStatus.NEW,
    )

    @Test
    fun `AddLead delegates to repository`() = runTest {
        coEvery { repository.add(testLead) } returns 1L
        val useCase = AddLeadUseCase(repository)

        val id = useCase(testLead)

        assertEquals(1L, id)
        coVerify { repository.add(testLead) }
    }

    @Test
    fun `GetLeadsByCase returns flow from repository`() = runTest {
        coEvery { repository.observeByCase(10) } returns flowOf(listOf(testLead))
        val useCase = GetLeadsByCaseUseCase(repository)

        val result = useCase(10).first()

        assertEquals(1, result.size)
        assertEquals("user123", result[0].matchedValue)
    }

    @Test
    fun `GetLeadsByCase returns empty list when no leads`() = runTest {
        coEvery { repository.observeByCase(99) } returns flowOf(emptyList())
        val useCase = GetLeadsByCaseUseCase(repository)

        val result = useCase(99).first()

        assertEquals(0, result.size)
    }

    @Test
    fun `UpdateLeadStatus delegates to repository`() = runTest {
        val useCase = UpdateLeadStatusUseCase(repository)

        useCase(1L, LeadStatus.CONFIRMED)

        coVerify { repository.updateStatus(1L, LeadStatus.CONFIRMED) }
    }

    @Test
    fun `UpdateLeadStatus with REJECTED status`() = runTest {
        val useCase = UpdateLeadStatusUseCase(repository)

        useCase(1L, LeadStatus.REJECTED)

        coVerify { repository.updateStatus(1L, LeadStatus.REJECTED) }
    }
}
