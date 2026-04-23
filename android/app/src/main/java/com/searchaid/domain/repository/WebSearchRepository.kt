package com.searchaid.domain.repository

import com.searchaid.domain.model.WebSearchResult

/**
 * Abstraction for web search execution. Implementations may use
 * Google Custom Search API for text and image search.
 */
interface WebSearchRepository {
    suspend fun search(query: String, maxResults: Int = 10): List<WebSearchResult>
    suspend fun searchImages(query: String, maxResults: Int = 10): List<WebSearchResult>
}
