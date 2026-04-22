package com.searchaid.domain.usecase

import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.repository.MissingCaseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CaseUseCasesTest {

    private val repository = mockk<MissingCaseRepository>(relaxed = true)

    private fun case_(id: Long = 1, status: CaseStatus = CaseStatus.ACTIVE) = MissingCase(
        id = id, personId = 42, status = status, createdAt = 1000,
        lastSeenTime = 900, lastSeenLocationName = "Park",
        lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = null, operatorId = null,
    )

    @Test
    fun `StartMissingCase creates case and returns id`() = runTest {
        coEvery { repository.create(any()) } returns 10L
        val useCase = StartMissingCaseUseCase(repository)

        val result = useCase(case_())

        assertEquals(10L, result)
        coVerify { repository.create(any()) }
    }

    @Test
    fun `GetMissingCase returns case by id`() = runTest {
        val expected = case_(5)
        coEvery { repository.getById(5) } returns expected
        val useCase = GetMissingCaseUseCase(repository)

        assertEquals(expected, useCase(5))
    }

    @Test
    fun `GetActiveCases returns only active cases`() = runTest {
        val cases = listOf(case_(1), case_(2))
        coEvery { repository.observeActive() } returns flowOf(cases)
        val useCase = GetActiveCasesUseCase(repository)

        val result = useCase().first()

        assertEquals(2, result.size)
        result.forEach { assertEquals(CaseStatus.ACTIVE, it.status) }
    }

    @Test
    fun `UpdateCaseStatus delegates to repository`() = runTest {
        val useCase = UpdateCaseStatusUseCase(repository)

        useCase(1, CaseStatus.FOUND)

        coVerify { repository.updateStatus(1, CaseStatus.FOUND) }
    }

    @Test
    fun `GetCasesByPerson returns cases for person`() = runTest {
        val cases = listOf(case_(1), case_(2, CaseStatus.CLOSED))
        coEvery { repository.observeByPerson(42) } returns flowOf(cases)
        val useCase = GetCasesByPersonUseCase(repository)

        val result = useCase(42).first()

        assertEquals(2, result.size)
    }
}
