package com.searchaid.domain.usecase

import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.repository.HistoricalPlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceUseCasesTest {

    private val repository = mockk<HistoricalPlaceRepository>(relaxed = true)

    @Test
    fun `AddHistoricalPlace delegates to repository`() = runTest {
        coEvery { repository.add(any()) } returns 5L
        val useCase = AddHistoricalPlaceUseCase(repository)

        val place = HistoricalPlace(
            personId = 1, title = "Home", lat = 55.75, lon = 37.61,
            source = "family", note = null,
        )

        val result = useCase(place)

        assertEquals(5L, result)
        coVerify { repository.add(place) }
    }

    @Test
    fun `DeleteHistoricalPlace delegates to repository`() = runTest {
        val useCase = DeleteHistoricalPlaceUseCase(repository)

        useCase(3)

        coVerify { repository.delete(3) }
    }

    @Test
    fun `GetHistoricalPlaces returns places for person`() = runTest {
        val places = listOf(
            HistoricalPlace(1, 42, "Park", 55.75, 37.61, null, null),
            HistoricalPlace(2, 42, "Shop", 55.76, 37.62, null, null),
        )
        coEvery { repository.observeByPerson(42) } returns flowOf(places)
        val useCase = GetHistoricalPlacesUseCase(repository)

        val result = useCase(42).first()

        assertEquals(2, result.size)
        assertEquals("Park", result[0].title)
    }
}
