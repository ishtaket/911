package com.searchaid.data.repository

import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.repository.WebSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of WebSearchRepository.
 * Returns empty results — replace with real API implementation
 * (Google Custom Search, Bing, etc.) when API keys are available.
 */
@Singleton
class StubWebSearchRepository @Inject constructor() : WebSearchRepository {
    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> = emptyList()
    override suspend fun searchImages(query: String, maxResults: Int): List<WebSearchResult> = emptyList()
}
