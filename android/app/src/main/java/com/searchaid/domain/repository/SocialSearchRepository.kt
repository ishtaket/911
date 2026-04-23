package com.searchaid.domain.repository

import com.searchaid.domain.model.SocialSearchResult

/**
 * Abstraction for social network profile search. Implementations may
 * query public APIs (VK, OK, Telegram) or use a local stub.
 */
interface SocialSearchRepository {
    suspend fun searchByHandle(platform: String, handle: String): List<SocialSearchResult>
    suspend fun searchByName(platform: String, name: String, region: String?): List<SocialSearchResult>
}
