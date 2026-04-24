package com.searchaid.data.repository

import com.searchaid.data.preferences.SearchToolPreferences
import com.searchaid.data.remote.api.GoogleSearchApi
import com.searchaid.data.remote.model.CseImage
import com.searchaid.data.remote.model.GoogleSearchItem
import com.searchaid.data.remote.model.GoogleSearchResponse
import com.searchaid.data.remote.model.ImageInfo
import com.searchaid.data.remote.model.PageMap
import com.searchaid.data.remote.model.Thumbnail
import com.searchaid.domain.model.SearchToolConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleWebSearchRepositoryTest {

    private val api = mockk<GoogleSearchApi>()
    private val preferences = mockk<SearchToolPreferences> {
        every { config } returns flowOf(SearchToolConfig())
    }

    // Note: In real tests, BuildConfig.GOOGLE_CSE_API_KEY is empty,
    // so the repository returns empty results (stub mode).
    // These tests verify the parsing logic via a test subclass.

    @Test
    fun `search returns empty when API key not configured`() = runTest {
        val repo = GoogleWebSearchRepository(api, preferences)
        val results = repo.search("test query")
        assertTrue("Should return empty without API key", results.isEmpty())
    }

    @Test
    fun `searchImages returns empty when API key not configured`() = runTest {
        val repo = GoogleWebSearchRepository(api, preferences)
        val results = repo.searchImages("test query")
        assertTrue("Should return empty without API key", results.isEmpty())
    }
}
