package com.searchaid.data.repository

import com.searchaid.BuildConfig
import com.searchaid.data.remote.api.GoogleSearchApi
import com.searchaid.data.remote.model.GoogleSearchItem
import com.searchaid.domain.model.SocialMatchType
import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.repository.SocialSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real social search using Google Custom Search with site: restrictions.
 * Searches public profiles on Facebook, Instagram, TikTok via Google index.
 *
 * Requires GOOGLE_CSE_API_KEY and GOOGLE_CSE_CX in local.properties.
 */
@Singleton
class GoogleSocialSearchRepository @Inject constructor(
    private val api: GoogleSearchApi,
) : SocialSearchRepository {

    private val apiKey: String = BuildConfig.GOOGLE_CSE_API_KEY
    private val cx: String = BuildConfig.GOOGLE_CSE_CX

    private val isConfigured: Boolean
        get() = apiKey.isNotBlank() && cx.isNotBlank()

    private val platformDomains = mapOf(
        "Facebook" to "facebook.com",
        "Instagram" to "instagram.com",
        "TikTok" to "tiktok.com",
    )

    override suspend fun searchByHandle(platform: String, handle: String): List<SocialSearchResult> {
        if (!isConfigured) return emptyList()
        val domain = platformDomains[platform] ?: return emptyList()
        return executeSearch(
            query = handle,
            platform = platform,
            siteSearch = domain,
            matchType = SocialMatchType.HANDLE_EXACT,
        )
    }

    override suspend fun searchByName(platform: String, name: String, region: String?): List<SocialSearchResult> {
        if (!isConfigured) return emptyList()
        val domain = platformDomains[platform] ?: return emptyList()
        val query = if (region != null) "$name $region" else name
        return executeSearch(
            query = query,
            platform = platform,
            siteSearch = domain,
            matchType = SocialMatchType.NAME_PARTIAL,
        )
    }

    private suspend fun executeSearch(
        query: String,
        platform: String,
        siteSearch: String,
        matchType: SocialMatchType,
    ): List<SocialSearchResult> {
        return try {
            val response = api.search(
                query = query,
                key = apiKey,
                cx = cx,
                siteSearch = siteSearch,
                num = 10,
            )
            response.items?.mapNotNull { item ->
                item.toSocialResult(platform, query, matchType)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun GoogleSearchItem.toSocialResult(
        platform: String,
        query: String,
        baseMatchType: SocialMatchType,
    ): SocialSearchResult? {
        val url = link ?: return null
        val profileName = extractProfileName(title, platform)
        val handle = extractHandle(url, platform)
        val matchType = refineMatchType(baseMatchType, query, profileName, handle)

        return SocialSearchResult(
            caseId = 0, // Will be set by use case
            platform = platform,
            profileName = profileName,
            profileUrl = url,
            handle = handle,
            snippet = snippet,
            avatarUrl = pagemap?.cseThumbnail?.firstOrNull()?.src,
            matchType = matchType,
            relevanceScore = calculateRelevance(matchType, query, profileName, handle),
        )
    }

    private fun extractProfileName(title: String?, platform: String): String {
        if (title == null) return "Unknown"
        // Strip common platform suffixes from titles
        return title
            .replace(" | Facebook", "")
            .replace(" - Facebook", "")
            .replace("(@\\S+) • Instagram.*".toRegex(), "$1")
            .replace(" on Instagram.*".toRegex(), "")
            .replace("(@\\S+) \\| TikTok".toRegex(), "$1")
            .replace(" | TikTok", "")
            .trim()
            .ifBlank { "Unknown" }
    }

    private fun extractHandle(url: String, platform: String): String? {
        return try {
            val path = url.substringAfter("://")
                .substringAfter("/")
                .trimEnd('/')
                .split("/")
                .firstOrNull()
                ?.takeIf { it.isNotBlank() && it != "p" && it != "reel" && it != "stories" }
            path
        } catch (e: Exception) {
            null
        }
    }

    private fun refineMatchType(
        base: SocialMatchType,
        query: String,
        profileName: String,
        handle: String?,
    ): SocialMatchType {
        val queryLower = query.lowercase()
        val nameLower = profileName.lowercase()
        val handleLower = handle?.lowercase()

        return when {
            handleLower != null && handleLower == queryLower -> SocialMatchType.HANDLE_EXACT
            handleLower != null && handleLower.contains(queryLower) -> SocialMatchType.HANDLE_PARTIAL
            nameLower == queryLower -> SocialMatchType.NAME_EXACT
            nameLower.contains(queryLower) || queryLower.contains(nameLower) -> SocialMatchType.NAME_PARTIAL
            else -> base
        }
    }

    private fun calculateRelevance(
        matchType: SocialMatchType,
        query: String,
        profileName: String,
        handle: String?,
    ): Float {
        val baseScore = when (matchType) {
            SocialMatchType.HANDLE_EXACT -> 0.95f
            SocialMatchType.NAME_EXACT -> 0.85f
            SocialMatchType.HANDLE_PARTIAL -> 0.65f
            SocialMatchType.NAME_PARTIAL -> 0.50f
            SocialMatchType.GROUP_MENTION -> 0.30f
        }
        return baseScore
    }
}
