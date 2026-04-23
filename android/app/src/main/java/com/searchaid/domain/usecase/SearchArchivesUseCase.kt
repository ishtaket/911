package com.searchaid.domain.usecase

import com.searchaid.domain.model.ArchiveResult
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.repository.ArchiveRepository
import javax.inject.Inject

/**
 * Searches web archives for cached social profiles.
 * Uses name variants from IdentityPack to find archived pages.
 */
class SearchArchivesUseCase @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) {
    suspend operator fun invoke(
        pack: IdentityPack,
        platforms: List<String> = listOf("Facebook", "Instagram", "TikTok"),
        limit: Int = 30,
    ): List<ArchiveResult> {
        val results = mutableListOf<ArchiveResult>()
        val seenUrls = mutableSetOf<String>()

        // Search by handles first
        for (handle in pack.handles.take(3)) {
            val archiveResults = archiveRepository.searchByName(handle, platforms, limit / 2)
            for (result in archiveResults) {
                if (seenUrls.add(result.archiveUrl)) {
                    results.add(result)
                }
            }
        }

        // Then search by name variants
        val nameQueries = pack.nameVariants
            .filter { it.contains(" ") }
            .take(2)
            .map { it.replace(" ", ".").lowercase() }

        for (name in nameQueries) {
            val archiveResults = archiveRepository.searchByName(name, platforms, limit / 2)
            for (result in archiveResults) {
                if (seenUrls.add(result.archiveUrl)) {
                    results.add(result)
                }
            }
        }

        return results.take(limit)
    }
}
