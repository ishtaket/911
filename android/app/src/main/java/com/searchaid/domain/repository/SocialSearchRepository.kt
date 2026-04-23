package com.searchaid.domain.repository

import com.searchaid.domain.model.SocialSearchResult

/**
 * Abstraction for social network profile search. Implementations may
 * search public profiles via Google CSE with site: operators (Facebook, Instagram, TikTok).
 */
interface SocialSearchRepository {
    suspend fun searchByHandle(platform: String, handle: String): List<SocialSearchResult>
    suspend fun searchByName(platform: String, name: String, region: String?): List<SocialSearchResult>
}
