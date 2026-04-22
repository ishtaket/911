package com.searchaid.domain.usecase

import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.repository.PersonProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileUseCasesTest {

    private val repository = mockk<PersonProfileRepository>(relaxed = true)

    private fun profile(id: Long = 1, name: String = "Test") = PersonProfile(
        id = id, name = name, age = 70, photoUri = null,
        condition = "Dementia", distinguishingFeatures = null,
        habits = null, knownLocations = null,
        aliases = emptyList(), nicknames = emptyList(),
        emails = emptyList(), phones = emptyList(),
        familyNotes = null, createdAt = 1000, updatedAt = 2000,
    )

    @Test
    fun `CreatePersonProfile delegates to repository`() = runTest {
        coEvery { repository.create(any()) } returns 42L
        val useCase = CreatePersonProfileUseCase(repository)

        val result = useCase(profile())

        assertEquals(42L, result)
        coVerify { repository.create(any()) }
    }

    @Test
    fun `GetPersonProfile returns profile when exists`() = runTest {
        val expected = profile(id = 5)
        coEvery { repository.getById(5) } returns expected
        val useCase = GetPersonProfileUseCase(repository)

        val result = useCase(5)

        assertEquals(expected, result)
    }

    @Test
    fun `GetPersonProfile returns null when not found`() = runTest {
        coEvery { repository.getById(99) } returns null
        val useCase = GetPersonProfileUseCase(repository)

        assertNull(useCase(99))
    }

    @Test
    fun `GetAllProfiles returns flow from repository`() = runTest {
        val profiles = listOf(profile(1, "A"), profile(2, "B"))
        coEvery { repository.observeAll() } returns flowOf(profiles)
        val useCase = GetAllProfilesUseCase(repository)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals("A", result[0].name)
        assertEquals("B", result[1].name)
    }

    @Test
    fun `UpdatePersonProfile delegates to repository`() = runTest {
        val useCase = UpdatePersonProfileUseCase(repository)
        val p = profile()

        useCase(p)

        coVerify { repository.update(p) }
    }

    @Test
    fun `DeletePersonProfile delegates to repository`() = runTest {
        val useCase = DeletePersonProfileUseCase(repository)

        useCase(7)

        coVerify { repository.delete(7) }
    }
}
