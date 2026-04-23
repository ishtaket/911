package com.searchaid.ui.feature_websearch

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.identity.NameNormalizer
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.model.ArchiveResult
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.WebSearchResult
import com.searchaid.domain.usecase.AddLeadUseCase
import com.searchaid.domain.usecase.BuildIdentityPackUseCase
import com.searchaid.domain.usecase.GenerateSearchQueriesUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.GetSocialSourcesByPersonUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.SearchArchivesUseCase
import com.searchaid.domain.usecase.SearchWebUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCase = mockk<GetMissingCaseUseCase>()
    private val getProfile = mockk<GetPersonProfileUseCase>()
    private val getSocialSources = mockk<GetSocialSourcesByPersonUseCase>()
    private val buildIdentityPack = BuildIdentityPackUseCase(NameNormalizer())
    private val generateQueries = GenerateSearchQueriesUseCase()
    private val searchWeb = mockk<SearchWebUseCase>()
    private val searchArchives = mockk<SearchArchivesUseCase>(relaxed = true)
    private val addLead = mockk<AddLeadUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testCase = MissingCase(
        id = 10, personId = 1, status = CaseStatus.ACTIVE,
        createdAt = 1000L, lastSeenTime = 900L,
        lastSeenLocationName = "Park", lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = null, notes = null, operatorId = null,
    )

    private val testProfile = PersonProfile(
        id = 1, name = "Иванов Иван", age = 72, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = null,
        habits = null, knownLocations = null, familyNotes = null,
    )

    private fun createViewModel(): WebSearchViewModel {
        coEvery { getCase(10L) } returns testCase
        coEvery { getProfile(1L) } returns testProfile
        every { getSocialSources(1L) } returns flowOf(emptyList())
        return WebSearchViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getProfile, getSocialSources,
            buildIdentityPack, generateQueries, searchWeb,
            searchArchives, addLead, logAction,
        )
    }

    @Test
    fun `init builds identity pack and generates queries`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.identityPack)
        assertEquals("Иванов Иван", vm.state.value.identityPack?.primaryName)
        assertTrue(vm.state.value.queries.isNotEmpty())
    }

    @Test
    fun `runSearch calls searchWeb and updates results`() = runTest {
        val result = WebSearchResult(
            caseId = 10, query = "test", title = "Found",
            snippet = "Match", url = "https://example.com",
            source = "google", relevanceScore = 0.8f,
        )
        coEvery { searchWeb(any(), any(), any(), any(), any()) } returns listOf(result)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.runSearch()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.results.size)
        assertFalse(vm.state.value.searching)
        coVerify { logAction("WEB_SEARCH", caseId = 10L, details = any()) }
    }

    @Test
    fun `promoteToLead creates lead and marks result`() = runTest {
        val result = WebSearchResult(
            caseId = 10, query = "test", title = "Found",
            snippet = "Match", url = "https://example.com",
            source = "google", relevanceScore = 0.8f,
        )
        coEvery { searchWeb(any(), any(), any(), any(), any()) } returns listOf(result)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.runSearch()
        advanceUntilIdle()

        vm.promoteToLead(vm.state.value.results[0])
        advanceUntilIdle()

        coVerify { addLead(match { it.type.name == "WEB" }) }
        assertEquals(SearchResultStatus.PROMOTED_TO_LEAD, vm.state.value.results[0].status)
    }

    @Test
    fun `dismissResult marks result as dismissed`() = runTest {
        val result = WebSearchResult(
            caseId = 10, query = "test", title = "Irrelevant",
            snippet = "Not a match", url = "https://spam.com",
            source = "google", relevanceScore = 0.1f,
        )
        coEvery { searchWeb(any(), any(), any(), any(), any()) } returns listOf(result)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.runSearch()
        advanceUntilIdle()

        vm.dismissResult(vm.state.value.results[0])

        assertEquals(SearchResultStatus.DISMISSED, vm.state.value.results[0].status)
    }

    @Test
    fun `runSearch includes archive results`() = runTest {
        val webResult = WebSearchResult(
            caseId = 10, query = "test", title = "Found",
            snippet = "Match", url = "https://example.com",
            source = "google", relevanceScore = 0.8f,
        )
        val archiveResult = ArchiveResult(
            originalUrl = "https://facebook.com/user",
            archiveUrl = "https://web.archive.org/web/20240101/https://facebook.com/user",
            timestamp = "20240101",
            platform = "Facebook",
        )
        coEvery { searchWeb(any(), any(), any(), any(), any()) } returns listOf(webResult)
        coEvery { searchArchives(any(), any()) } returns listOf(archiveResult)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.runSearch()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.results.size)
        assertEquals(1, vm.state.value.archiveResults.size)
        assertEquals("Facebook", vm.state.value.archiveResults[0].platform)
    }

    @Test
    fun `missing case shows error`() = runTest {
        coEvery { getCase(10L) } returns null
        val vm = WebSearchViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getProfile, getSocialSources,
            buildIdentityPack, generateQueries, searchWeb,
            searchArchives, addLead, logAction,
        )
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.error)
    }
}
