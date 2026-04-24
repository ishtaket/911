package com.searchaid.domain.usecase

import com.searchaid.data.preferences.SearchToolPreferences
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.SocialMatchType
import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.model.SocialSource
import com.searchaid.domain.repository.SocialSearchRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Searches social networks using handles from SocialSources and
 * name variants from IdentityPack. Deduplicates by profile URL.
 * Respects enabled platforms from user preferences.
 */
class SearchSocialUseCase @Inject constructor(
    private val repository: SocialSearchRepository,
    private val preferences: SearchToolPreferences,
) {
    suspend operator fun invoke(
        pack: IdentityPack,
        sources: List<SocialSource>,
        caseId: Long,
    ): List<SocialSearchResult> {
        val config = preferences.config.first()
        val allResults = mutableListOf<SocialSearchResult>()
        val seenUrls = mutableSetOf<String>()

        // 1. Search by known handles (highest priority)
        for (source in sources.filter { it.enabled && !it.handleOrAlias.isNullOrBlank() && config.isPlatformEnabled(it.platform) }) {
            val results = repository.searchByHandle(source.platform, source.handleOrAlias!!)
            for (result in results) {
                if (seenUrls.add(result.profileUrl)) {
                    allResults.add(result.copy(caseId = caseId))
                }
            }
        }

        // 2. Search by name on platforms where we have sources
        val platforms = sources.map { it.platform }.distinct().filter { config.isPlatformEnabled(it) }
        val nameQueries = pack.nameVariants.filter { it.contains(" ") }.take(3)

        for (platform in platforms) {
            for (name in nameQueries) {
                val results = repository.searchByName(platform, name, pack.region)
                for (result in results) {
                    if (seenUrls.add(result.profileUrl)) {
                        allResults.add(result.copy(caseId = caseId))
                    }
                }
            }
        }

        // 3. If no specific platforms, search enabled ones by name
        if (platforms.isEmpty()) {
            val defaultPlatforms = config.enabledPlatforms
            for (platform in defaultPlatforms) {
                val results = repository.searchByName(platform, pack.primaryName, pack.region)
                for (result in results) {
                    if (seenUrls.add(result.profileUrl)) {
                        allResults.add(result.copy(caseId = caseId))
                    }
                }
            }
        }

        return allResults.sortedWith(
            compareByDescending<SocialSearchResult> { it.matchType.priority() }
                .thenByDescending { it.relevanceScore }
        )
    }

    private fun SocialMatchType.priority(): Int = when (this) {
        SocialMatchType.HANDLE_EXACT -> 5
        SocialMatchType.NAME_EXACT -> 4
        SocialMatchType.HANDLE_PARTIAL -> 3
        SocialMatchType.NAME_PARTIAL -> 2
        SocialMatchType.GROUP_MENTION -> 1
    }
}
