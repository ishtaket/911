package com.searchaid.data.repository

import com.searchaid.data.remote.api.WaybackApi
import com.searchaid.domain.model.ArchiveResult
import com.searchaid.domain.repository.ArchiveRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wayback Machine archive search. Searches for cached versions of
 * social profiles and web pages. No API key required.
 */
@Singleton
class WaybackArchiveRepository @Inject constructor(
    private val api: WaybackApi,
) : ArchiveRepository {

    private val platformDomains = mapOf(
        "Facebook" to "facebook.com",
        "Instagram" to "instagram.com",
        "TikTok" to "tiktok.com",
    )

    override suspend fun searchArchives(url: String, limit: Int): List<ArchiveResult> {
        return try {
            val response = api.search(url = url, limit = limit)
            parseResponse(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchByName(name: String, platforms: List<String>, limit: Int): List<ArchiveResult> {
        val results = mutableListOf<ArchiveResult>()
        val perPlatform = maxOf(1, limit / maxOf(1, platforms.size))

        for (platform in platforms) {
            val domain = platformDomains[platform] ?: continue
            // Search for profile URLs containing the name
            val cleanName = name.lowercase()
                .replace(" ", ".")
                .replace(Regex("[^a-z0-9.]"), "")

            if (cleanName.isBlank()) continue

            try {
                val response = api.search(
                    url = "$domain/$cleanName*",
                    limit = perPlatform,
                    matchType = "prefix",
                )
                val parsed = parseResponse(response).map {
                    it.copy(platform = platform)
                }
                results.addAll(parsed)
            } catch (e: Exception) {
                // Continue with other platforms
            }
        }
        return results
    }

    private fun parseResponse(response: List<List<String>>): List<ArchiveResult> {
        // First row is header: [urlkey, timestamp, original, mimetype, statuscode, digest, length]
        return response.drop(1).mapNotNull { row ->
            if (row.size < 3) return@mapNotNull null
            val timestamp = row[1]
            val originalUrl = row[2]
            val archiveUrl = "https://web.archive.org/web/$timestamp/$originalUrl"
            val platform = platformDomains.entries
                .firstOrNull { originalUrl.contains(it.value) }
                ?.key

            ArchiveResult(
                originalUrl = originalUrl,
                archiveUrl = archiveUrl,
                timestamp = timestamp,
                platform = platform,
            )
        }
    }
}
