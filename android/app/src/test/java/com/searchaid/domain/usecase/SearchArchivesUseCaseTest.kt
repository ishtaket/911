package com.searchaid.domain.usecase

import com.searchaid.domain.model.ArchiveResult
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.repository.ArchiveRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchArchivesUseCaseTest {

    private val archiveRepo = mockk<ArchiveRepository>()
    private val useCase = SearchArchivesUseCase(archiveRepo)

    private val pack = IdentityPack(
        personId = 1L,
        primaryName = "John Doe",
        nameVariants = listOf("John Doe", "Doe John"),
        aliases = emptyList(),
        handles = listOf("johndoe"),
        emails = emptyList(),
        phones = emptyList(),
        age = 72,
        region = null,
    )

    @Test
    fun `searches by handles and name variants`() = runTest {
        coEvery { archiveRepo.searchByName(any(), any(), any()) } returns listOf(
            ArchiveResult(
                originalUrl = "https://facebook.com/johndoe",
                archiveUrl = "https://web.archive.org/web/20240101/https://facebook.com/johndoe",
                timestamp = "20240101",
                platform = "Facebook",
            ),
        )

        val results = useCase(pack)
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `deduplicates by archive URL`() = runTest {
        val sameResult = ArchiveResult(
            originalUrl = "https://facebook.com/johndoe",
            archiveUrl = "https://web.archive.org/web/20240101/https://facebook.com/johndoe",
            timestamp = "20240101",
            platform = "Facebook",
        )
        coEvery { archiveRepo.searchByName(any(), any(), any()) } returns listOf(sameResult)

        val results = useCase(pack)
        assertEquals(1, results.count { it.archiveUrl == sameResult.archiveUrl })
    }

    @Test
    fun `returns empty when no archive results`() = runTest {
        coEvery { archiveRepo.searchByName(any(), any(), any()) } returns emptyList()

        val results = useCase(pack)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `respects limit parameter`() = runTest {
        val manyResults = (1..50).map {
            ArchiveResult(
                originalUrl = "https://facebook.com/user$it",
                archiveUrl = "https://web.archive.org/web/2024/$it",
                timestamp = "2024010$it",
                platform = "Facebook",
            )
        }
        coEvery { archiveRepo.searchByName(any(), any(), any()) } returns manyResults

        val results = useCase(pack, limit = 10)
        assertTrue(results.size <= 10)
    }
}
