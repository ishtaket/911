package com.searchaid.data.repository

import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.repository.SocialSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of SocialSearchRepository.
 * Returns empty results — replace with real API implementations
 * (VK API, OK API, Telegram Bot API) when keys are available.
 */
@Singleton
class StubSocialSearchRepository @Inject constructor() : SocialSearchRepository {
    override suspend fun searchByHandle(platform: String, handle: String): List<SocialSearchResult> = emptyList()
    override suspend fun searchByName(platform: String, name: String, region: String?): List<SocialSearchResult> = emptyList()
}
