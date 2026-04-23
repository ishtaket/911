package com.searchaid.domain.usecase

import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.repository.WebSearchRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWebUseCaseTest {

    private val repository = mockk<WebSearchRepository>()
    private val generateQueries = GenerateSearchQueriesUseCase()
    private val useCase = SearchWebUseCase(repository, generateQueries)

    private val pack = IdentityPack(
        personId = 1L,
        primaryName = "Иванов Иван",
        nameVariants = listOf("Иванов Иван", "Иван Иванов"),
        aliases = emptyList(),
        handles = emptyList(),
        emails = emptyList(),
        phones = emptyList(),
        age = null,
        region = null,
    )

    private fun result(url: String, title: String = "Result") = WebSearchResult(
        caseId = 0, query = "", title = title, snippet = "Snippet",
        url = url, source = "google", relevanceScore = 0.5f,
    )

    @Test
    fun `returns results from repository`() = runTest {
        coEvery { repository.search(any(), any()) } returns listOf(
            result("https://example.com/1", "Found person"),
        )

        val results = useCase(pack, caseId = 10L)
        assertTrue(results.isNotEmpty())
        assertEquals(10L, results[0].caseId)
    }

    @Test
    fun `deduplicates results by URL`() = runTest {
        coEvery { repository.search(any(), any()) } returns listOf(
            result("https://example.com/same"),
        )

        val results = useCase(pack, caseId = 10L, maxQueriesPerSearch = 3)
        // Same URL from multiple queries should be deduplicated
        assertEquals(1, results.count { it.url == "https://example.com/same" })
    }

    @Test
    fun `respects maxQueriesPerSearch limit`() = runTest {
        var queryCount = 0
        coEvery { repository.search(any(), any()) } answers {
            queryCount++
            emptyList()
        }

        useCase(pack, caseId = 10L, maxQueriesPerSearch = 2)
        assertEquals(2, queryCount)
    }

    @Test
    fun `results sorted by relevance descending`() = runTest {
        coEvery { repository.search(any(), any()) } returns listOf(
            result("https://low.com").copy(relevanceScore = 0.2f),
            result("https://high.com").copy(relevanceScore = 0.9f),
        )

        val results = useCase(pack, caseId = 10L, maxQueriesPerSearch = 1)
        assertTrue(results[0].relevanceScore >= results[1].relevanceScore)
    }

    @Test
    fun `empty repository returns empty results`() = runTest {
        coEvery { repository.search(any(), any()) } returns emptyList()
        val results = useCase(pack, caseId = 10L)
        assertTrue(results.isEmpty())
    }
}
