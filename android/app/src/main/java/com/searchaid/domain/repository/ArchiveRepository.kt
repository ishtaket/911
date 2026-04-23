package com.searchaid.domain.repository

import com.searchaid.domain.model.ArchiveResult

/**
 * Search web archives for cached/historical versions of social profiles.
 * Uses Wayback Machine CDX API — no API key required.
 */
interface ArchiveRepository {
    suspend fun searchArchives(url: String, limit: Int = 20): List<ArchiveResult>
    suspend fun searchByName(name: String, platforms: List<String>, limit: Int = 20): List<ArchiveResult>
}
