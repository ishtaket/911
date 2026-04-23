package com.searchaid.data.repository

import com.searchaid.BuildConfig
import com.searchaid.data.remote.api.GoogleSearchApi
import com.searchaid.data.remote.model.GoogleSearchItem
import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.repository.WebSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of WebSearchRepository using Google Custom Search JSON API.
 * Supports both text search and image search.
 *
 * Requires GOOGLE_CSE_API_KEY and GOOGLE_CSE_CX in local.properties.
 * Falls back to empty results when keys are not configured.
 */
@Singleton
class GoogleWebSearchRepository @Inject constructor(
    private val api: GoogleSearchApi,
) : WebSearchRepository {

    private val apiKey: String = BuildConfig.GOOGLE_CSE_API_KEY
    private val cx: String = BuildConfig.GOOGLE_CSE_CX

    private val isConfigured: Boolean
        get() = apiKey.isNotBlank() && cx.isNotBlank()

    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        if (!isConfigured) return emptyList()
        return executeSearch(query, maxResults, searchType = null)
    }

    override suspend fun searchImages(query: String, maxResults: Int): List<WebSearchResult> {
        if (!isConfigured) return emptyList()
        return executeSearch(query, maxResults, searchType = "image")
    }

    private suspend fun executeSearch(
        query: String,
        maxResults: Int,
        searchType: String?,
    ): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        // Google CSE returns max 10 results per request, paginate if needed
        val pages = (maxResults + 9) / 10
        for (page in 0 until pages) {
            val startIndex = page * 10 + 1
            val num = minOf(10, maxResults - results.size)
            if (num <= 0) break

            try {
                val response = api.search(
                    query = query,
                    key = apiKey,
                    cx = cx,
                    searchType = searchType,
                    num = num,
                    start = startIndex,
                )
                response.items?.forEach { item ->
                    val result = item.toWebSearchResult(
                        query = query,
                        source = if (searchType == "image") "google_images" else "google",
                        isImageSearch = searchType == "image",
                    )
                    if (result != null) results.add(result)
                }
                // Stop if no more results
                if (response.items.isNullOrEmpty()) break
            } catch (e: Exception) {
                // Log error but don't crash — partial results are better than none
                break
            }
        }
        return results
    }

    private fun GoogleSearchItem.toWebSearchResult(
        query: String,
        source: String,
        isImageSearch: Boolean,
    ): WebSearchResult? {
        val url = link ?: return null
        return WebSearchResult(
            caseId = 0, // Will be set by use case
            query = query,
            title = title ?: url,
            snippet = snippet ?: "",
            url = url,
            source = source,
            thumbnailUrl = when {
                isImageSearch -> image?.thumbnailLink
                else -> pagemap?.cseThumbnail?.firstOrNull()?.src
            },
            imageUrl = when {
                isImageSearch -> link
                else -> pagemap?.cseImage?.firstOrNull()?.src
            },
        )
    }
}
