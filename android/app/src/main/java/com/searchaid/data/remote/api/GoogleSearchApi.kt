package com.searchaid.data.remote.api

import com.searchaid.data.remote.model.GoogleSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Custom Search JSON API.
 * Base URL: https://www.googleapis.com/customsearch/
 *
 * Requires:
 * - API Key (from Google Cloud Console → APIs & Services → Credentials)
 * - Search Engine ID (cx) (from Programmable Search Engine → Control Panel)
 *
 * Free tier: 100 queries/day. Paid: $5 per 1000 queries.
 */
interface GoogleSearchApi {

    /**
     * Search the web.
     * @param query Search query text
     * @param key Google API key
     * @param cx Search engine ID
     * @param searchType null for web results, "image" for image results
     * @param siteSearch Restrict results to a specific site (e.g., "facebook.com")
     * @param num Number of results (1-10)
     * @param start Start index for pagination (1-based)
     */
    @GET("v1")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") key: String,
        @Query("cx") cx: String,
        @Query("searchType") searchType: String? = null,
        @Query("siteSearch") siteSearch: String? = null,
        @Query("num") num: Int = 10,
        @Query("start") start: Int = 1,
    ): GoogleSearchResponse
}
