package com.searchaid.domain.repository

import com.searchaid.domain.model.WebSearchResult

/**
 * Abstraction for web search execution. Implementations may use
 * Google Custom Search API, Bing API, or a local stub for testing.
 */
interface WebSearchRepository {
    suspend fun search(query: String, maxResults: Int = 10): List<WebSearchResult>
}
