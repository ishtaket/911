package com.searchaid.domain.usecase

import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.repository.SearchZoneRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchZoneUseCasesTest {

    private val repository = mockk<SearchZoneRepository>(relaxed = true)

    private val testZone = SearchZone(
        id = 1, caseId = 10, lat = 55.75, lon = 37.61,
        radiusMeters = 500.0, score = 0.8f, reason = "Historical place",
        checked = false,
    )

    @Test
    fun `AddSearchZone delegates to repository`() = runTest {
        coEvery { repository.add(testZone) } returns 1L
        val useCase = AddSearchZoneUseCase(repository)

        val id = useCase(testZone)

        assertEquals(1L, id)
        coVerify { repository.add(testZone) }
    }

    @Test
    fun `GetSearchZonesByCase returns flow from repository`() = runTest {
        coEvery { repository.observeByCase(10) } returns flowOf(listOf(testZone))
        val useCase = GetSearchZonesByCaseUseCase(repository)

        val result = useCase(10).first()

        assertEquals(1, result.size)
        assertEquals("Historical place", result[0].reason)
    }

    @Test
    fun `GetSearchZonesByCase returns empty list when no zones`() = runTest {
        coEvery { repository.observeByCase(99) } returns flowOf(emptyList())
        val useCase = GetSearchZonesByCaseUseCase(repository)

        val result = useCase(99).first()

        assertEquals(0, result.size)
    }

    @Test
    fun `MarkZoneChecked delegates to repository`() = runTest {
        val useCase = MarkZoneCheckedUseCase(repository)

        useCase(1L)

        coVerify { repository.markChecked(1L) }
    }
}
