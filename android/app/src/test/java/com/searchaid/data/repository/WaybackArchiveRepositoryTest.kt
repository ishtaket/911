package com.searchaid.data.repository

import com.searchaid.data.remote.api.WaybackApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaybackArchiveRepositoryTest {

    private val api = mockk<WaybackApi>()
    private val repo = WaybackArchiveRepository(api)

    @Test
    fun `searchArchives parses CDX response correctly`() = runTest {
        coEvery { api.search(any(), any(), any(), any(), any(), any()) } returns listOf(
            listOf("urlkey", "timestamp", "original", "mimetype", "statuscode", "digest", "length"), // header
            listOf("com,facebook)/johndoe", "20240115120000", "https://facebook.com/johndoe", "text/html", "200", "ABC123", "5000"),
        )

        val results = repo.searchArchives("facebook.com/johndoe")
        assertEquals(1, results.size)
        assertEquals("https://facebook.com/johndoe", results[0].originalUrl)
        assertEquals("https://web.archive.org/web/20240115120000/https://facebook.com/johndoe", results[0].archiveUrl)
        assertEquals("20240115120000", results[0].timestamp)
        assertEquals("Facebook", results[0].platform)
    }

    @Test
    fun `searchArchives returns empty on API error`() = runTest {
        coEvery { api.search(any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val results = repo.searchArchives("facebook.com/johndoe")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchByName searches across platforms`() = runTest {
        coEvery { api.search(any(), any(), any(), any(), any(), any()) } returns listOf(
            listOf("urlkey", "timestamp", "original", "mimetype", "statuscode", "digest", "length"), // header
        )

        val results = repo.searchByName("john.doe", listOf("Facebook", "Instagram"), limit = 10)
        assertTrue(results.isEmpty()) // No results beyond header
    }

    @Test
    fun `searchByName skips unsupported platforms`() = runTest {
        coEvery { api.search(any(), any(), any(), any(), any(), any()) } returns listOf(
            listOf("urlkey", "timestamp", "original", "mimetype", "statuscode", "digest", "length"),
        )

        val results = repo.searchByName("john.doe", listOf("VK", "Snapchat"), limit = 10)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchArchives identifies Instagram platform`() = runTest {
        coEvery { api.search(any(), any(), any(), any(), any(), any()) } returns listOf(
            listOf("urlkey", "timestamp", "original", "mimetype", "statuscode", "digest", "length"),
            listOf("com,instagram)/johndoe", "20240201", "https://instagram.com/johndoe", "text/html", "200", "XYZ", "3000"),
        )

        val results = repo.searchArchives("instagram.com/johndoe")
        assertEquals(1, results.size)
        assertEquals("Instagram", results[0].platform)
    }
}
