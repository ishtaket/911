package com.searchaid.data.repository

import com.searchaid.BuildConfig
import com.searchaid.data.preferences.SearchToolPreferences
import com.searchaid.data.remote.api.GoogleSearchApi
import com.searchaid.data.remote.model.GoogleSearchItem
import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.repository.WebSearchRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of WebSearchRepository using Google Custom Search JSON API.
 * Supports both text search and image search.
 *
 * Reads API keys from DataStore preferences (set in Settings/Onboarding).
 * Falls back to BuildConfig keys if DataStore is empty.
 * Falls back to empty results when no keys are configured anywhere.
 */
@Singleton
class GoogleWebSearchRepository @Inject constructor(
    private val api: GoogleSearchApi,
    private val preferences: SearchToolPreferences,
) : WebSearchRepository {

    private suspend fun getKeys(): Pair<String, String> {
        val config = preferences.config.first()
        val key = config.googleApiKey.ifBlank { BuildConfig.GOOGLE_CSE_API_KEY }
        val cx = config.googleCx.ifBlank { BuildConfig.GOOGLE_CSE_CX }
        return key to cx
    }

    override suspend fun search(query: String, maxResults: Int): List<WebSearchResult> {
        val (apiKey, cx) = getKeys()
        if (apiKey.isBlank() || cx.isBlank()) return emptyList()
        return executeSearch(query, maxResults, searchType = null, apiKey = apiKey, cx = cx)
    }

    override suspend fun searchImages(query: String, maxResults: Int): List<WebSearchResult> {
        val (apiKey, cx) = getKeys()
        if (apiKey.isBlank() || cx.isBlank()) return emptyList()
        return executeSearch(query, maxResults, searchType = "image", apiKey = apiKey, cx = cx)
    }

    private suspend fun executeSearch(
        query: String,
        maxResults: Int,
        searchType: String?,
        apiKey: String,
        cx: String,
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
