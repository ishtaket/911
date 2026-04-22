package com.searchaid.domain.usecase

import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.repository.SocialSourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialSourceUseCasesTest {

    private val repository = mockk<SocialSourceRepository>(relaxed = true)

    private val testSource = SocialSource(
        id = 1, personId = 10, platform = "Telegram",
        sourceType = "messenger", title = "Main account",
        handleOrAlias = "@user", region = "Moscow", url = null,
        visibility = "public", enabled = true,
    )

    @Test
    fun `AddSocialSource delegates to repository`() = runTest {
        coEvery { repository.add(testSource) } returns 1L
        val useCase = AddSocialSourceUseCase(repository)

        val id = useCase(testSource)

        assertEquals(1L, id)
        coVerify { repository.add(testSource) }
    }

    @Test
    fun `GetSocialSourcesByPerson returns flow from repository`() = runTest {
        coEvery { repository.observeByPerson(10) } returns flowOf(listOf(testSource))
        val useCase = GetSocialSourcesByPersonUseCase(repository)

        val result = useCase(10).first()

        assertEquals(1, result.size)
        assertEquals("Telegram", result[0].platform)
    }

    @Test
    fun `GetSocialSourcesByPerson returns empty list when no sources`() = runTest {
        coEvery { repository.observeByPerson(99) } returns flowOf(emptyList())
        val useCase = GetSocialSourcesByPersonUseCase(repository)

        val result = useCase(99).first()

        assertEquals(0, result.size)
    }

    @Test
    fun `DeleteSocialSource delegates to repository`() = runTest {
        val useCase = DeleteSocialSourceUseCase(repository)

        useCase(1L)

        coVerify { repository.delete(1L) }
    }
}
