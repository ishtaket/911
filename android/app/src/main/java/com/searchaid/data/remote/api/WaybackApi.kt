package com.searchaid.data.remote.api

import com.searchaid.data.remote.model.WaybackCdxResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Wayback Machine CDX Server API.
 * Base URL: https://web.archive.org/
 * No API key required — public access.
 *
 * Returns archived snapshots for a given URL pattern.
 */
interface WaybackApi {

    /**
     * Search the CDX index for archived URLs.
     * @param url URL or URL pattern (supports wildcards like *.facebook.com/john.doe)
     * @param output Output format (always "json")
     * @param limit Max results
     * @param matchType How to match the URL: "exact", "prefix", "host", "domain"
     * @param filter Filter results (e.g., "statuscode:200" for successful snapshots)
     * @param collapse Collapse duplicate entries (e.g., "timestamp:8" = one per day)
     */
    @GET("cdx/search/cdx")
    suspend fun search(
        @Query("url") url: String,
        @Query("output") output: String = "json",
        @Query("limit") limit: Int = 20,
        @Query("matchType") matchType: String = "prefix",
        @Query("filter") filter: String = "statuscode:200",
        @Query("collapse") collapse: String = "timestamp:8",
    ): WaybackCdxResponse
}
