package com.searchaid.data.repository

import com.searchaid.data.remote.api.GoogleSearchApi
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSocialSearchRepositoryTest {

    private val api = mockk<GoogleSearchApi>()

    @Test
    fun `searchByHandle returns empty when API key not configured`() = runTest {
        val repo = GoogleSocialSearchRepository(api)
        val results = repo.searchByHandle("Facebook", "johndoe")
        assertTrue("Should return empty without API key", results.isEmpty())
    }

    @Test
    fun `searchByName returns empty when API key not configured`() = runTest {
        val repo = GoogleSocialSearchRepository(api)
        val results = repo.searchByName("Instagram", "John Doe", "New York")
        assertTrue("Should return empty without API key", results.isEmpty())
    }

    @Test
    fun `searchByHandle returns empty for unsupported platform`() = runTest {
        val repo = GoogleSocialSearchRepository(api)
        val results = repo.searchByHandle("Snapchat", "johndoe")
        assertTrue("Should return empty for unsupported platform", results.isEmpty())
    }

    @Test
    fun `searchByName returns empty for unsupported platform`() = runTest {
        val repo = GoogleSocialSearchRepository(api)
        val results = repo.searchByName("VK", "John Doe", null)
        assertTrue("Should return empty for unsupported platform", results.isEmpty())
    }
}
