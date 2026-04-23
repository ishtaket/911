package com.searchaid.domain.usecase

import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.SocialMatchType
import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.repository.SocialSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSocialUseCaseTest {

    private val repository = mockk<SocialSearchRepository>()
    private val useCase = SearchSocialUseCase(repository)

    private val pack = IdentityPack(
        personId = 1L,
        primaryName = "Иванов Иван",
        nameVariants = listOf("Иванов Иван", "Иван Иванов"),
        aliases = emptyList(),
        handles = listOf("ivanov_ivan"),
        emails = emptyList(),
        phones = emptyList(),
        age = 72,
        region = "Москва",
    )

    private fun result(
        url: String,
        platform: String = "VK",
        matchType: SocialMatchType = SocialMatchType.HANDLE_EXACT,
        score: Float = 0.5f,
    ) = SocialSearchResult(
        caseId = 0, platform = platform, profileName = "Test User",
        profileUrl = url, handle = "test", snippet = null, avatarUrl = null,
        matchType = matchType, relevanceScore = score,
    )

    private val sources = listOf(
        SocialSource(id = 1, personId = 1, platform = "VK", sourceType = "profile", title = null, handleOrAlias = "ivanov_ivan", region = null, url = null, visibility = null, enabled = true),
        SocialSource(id = 2, personId = 1, platform = "OK", sourceType = "profile", title = null, handleOrAlias = "ivan.ivanov", region = null, url = null, visibility = null, enabled = true),
    )

    @Test
    fun `searches by handle for enabled sources`() = runTest {
        coEvery { repository.searchByHandle("VK", "ivanov_ivan") } returns listOf(
            result("https://vk.com/ivanov_ivan"),
        )
        coEvery { repository.searchByHandle("OK", "ivan.ivanov") } returns emptyList()
        coEvery { repository.searchByName(any(), any(), any()) } returns emptyList()

        val results = useCase(pack, sources, caseId = 10L)
        assertEquals(1, results.size)
        assertEquals(10L, results[0].caseId)
        coVerify { repository.searchByHandle("VK", "ivanov_ivan") }
        coVerify { repository.searchByHandle("OK", "ivan.ivanov") }
    }

    @Test
    fun `skips disabled sources`() = runTest {
        val mixedSources = listOf(
            SocialSource(id = 1, personId = 1, platform = "VK", sourceType = "profile", title = null, handleOrAlias = "ivanov", region = null, url = null, visibility = null, enabled = true),
            SocialSource(id = 2, personId = 1, platform = "OK", sourceType = "profile", title = null, handleOrAlias = "ivanov", region = null, url = null, visibility = null, enabled = false),
        )
        coEvery { repository.searchByHandle("VK", "ivanov") } returns emptyList()
        coEvery { repository.searchByName(any(), any(), any()) } returns emptyList()

        useCase(pack, mixedSources, caseId = 10L)
        coVerify(exactly = 1) { repository.searchByHandle(any(), any()) }
        coVerify { repository.searchByHandle("VK", "ivanov") }
    }

    @Test
    fun `deduplicates results by profile URL`() = runTest {
        coEvery { repository.searchByHandle(any(), any()) } returns listOf(
            result("https://vk.com/same"),
        )
        coEvery { repository.searchByName(any(), any(), any()) } returns listOf(
            result("https://vk.com/same", matchType = SocialMatchType.NAME_EXACT),
        )

        val results = useCase(pack, sources, caseId = 10L)
        assertEquals(1, results.count { it.profileUrl == "https://vk.com/same" })
    }

    @Test
    fun `results sorted by match type priority then relevance`() = runTest {
        coEvery { repository.searchByHandle(any(), any()) } returns emptyList()
        coEvery { repository.searchByName(any(), any(), any()) } returns listOf(
            result("https://vk.com/1", matchType = SocialMatchType.NAME_PARTIAL, score = 0.9f),
            result("https://vk.com/2", matchType = SocialMatchType.NAME_EXACT, score = 0.5f),
        )

        val results = useCase(pack, sources, caseId = 10L)
        assertTrue(results.size >= 2)
        // NAME_EXACT (priority 4) should come before NAME_PARTIAL (priority 2)
        val exactIdx = results.indexOfFirst { it.matchType == SocialMatchType.NAME_EXACT }
        val partialIdx = results.indexOfFirst { it.matchType == SocialMatchType.NAME_PARTIAL }
        assertTrue("NAME_EXACT should rank higher", exactIdx < partialIdx)
    }

    @Test
    fun `uses default platforms when no sources provided`() = runTest {
        coEvery { repository.searchByName(any(), any(), any()) } returns emptyList()

        useCase(pack, emptyList(), caseId = 10L)
        coVerify { repository.searchByName("Facebook", any(), any()) }
        coVerify { repository.searchByName("Instagram", any(), any()) }
        coVerify { repository.searchByName("TikTok", any(), any()) }
    }

    @Test
    fun `empty repository returns empty results`() = runTest {
        coEvery { repository.searchByHandle(any(), any()) } returns emptyList()
        coEvery { repository.searchByName(any(), any(), any()) } returns emptyList()

        val results = useCase(pack, sources, caseId = 10L)
        assertTrue(results.isEmpty())
    }
}
