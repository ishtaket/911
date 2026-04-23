package com.searchaid.domain.usecase

import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.repository.WebSearchRepository
import javax.inject.Inject

/**
 * Executes web searches using queries from an IdentityPack.
 * Runs each query through the WebSearchRepository, deduplicates by URL,
 * and returns results sorted by relevance.
 */
class SearchWebUseCase @Inject constructor(
    private val repository: WebSearchRepository,
    private val generateQueries: GenerateSearchQueriesUseCase,
) {
    suspend operator fun invoke(
        pack: IdentityPack,
        caseId: Long,
        maxQueriesPerSearch: Int = 5,
        maxResultsPerQuery: Int = 10,
        includeImages: Boolean = false,
    ): List<WebSearchResult> {
        val queries = generateQueries(pack).take(maxQueriesPerSearch)
        val allResults = mutableListOf<WebSearchResult>()
        val seenUrls = mutableSetOf<String>()

        for (query in queries) {
            // Text search
            val results = repository.search(query, maxResultsPerQuery)
            for (result in results) {
                if (seenUrls.add(result.url)) {
                    allResults.add(result.copy(caseId = caseId, query = query))
                }
            }

            // Image search (use first 2 queries only to save API quota)
            if (includeImages && queries.indexOf(query) < 2) {
                val imageResults = repository.searchImages(query, maxOf(5, maxResultsPerQuery / 2))
                for (result in imageResults) {
                    if (seenUrls.add(result.url)) {
                        allResults.add(result.copy(caseId = caseId, query = query))
                    }
                }
            }
        }

        return allResults.sortedByDescending { it.relevanceScore }
    }
}
