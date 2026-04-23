package com.searchaid.ui.feature_social

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.identity.NameNormalizer
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.model.SearchResultStatus
import com.searchaid.domain.model.SocialMatchType
import com.searchaid.domain.model.SocialSearchResult
import com.searchaid.domain.usecase.AddLeadUseCase
import com.searchaid.domain.usecase.BuildIdentityPackUseCase
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.GetSocialSourcesByPersonUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.SearchSocialUseCase
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
class SocialSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCase = mockk<GetMissingCaseUseCase>()
    private val getProfile = mockk<GetPersonProfileUseCase>()
    private val getSocialSources = mockk<GetSocialSourcesByPersonUseCase>()
    private val buildIdentityPack = BuildIdentityPackUseCase(NameNormalizer())
    private val searchSocial = mockk<SearchSocialUseCase>()
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

    private fun createViewModel(): SocialSearchViewModel {
        coEvery { getCase(10L) } returns testCase
        coEvery { getProfile(1L) } returns testProfile
        every { getSocialSources(1L) } returns flowOf(emptyList())
        return SocialSearchViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getProfile, getSocialSources,
            buildIdentityPack, searchSocial,
            addLead, logAction,
        )
    }

    @Test
    fun `init builds identity pack and loads sources`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.identityPack)
        assertEquals("Иванов Иван", vm.state.value.identityPack?.primaryName)
    }

    @Test
    fun `runSearch calls searchSocial and updates results`() = runTest {
        val result = SocialSearchResult(
            caseId = 10, platform = "VK", profileName = "Иван Иванов",
            profileUrl = "https://vk.com/ivanov", handle = "ivanov",
            snippet = "Москва", avatarUrl = null,
            matchType = SocialMatchType.HANDLE_EXACT, relevanceScore = 0.8f,
        )
        coEvery { searchSocial(any(), any(), any()) } returns listOf(result)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.runSearch()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.results.size)
        assertFalse(vm.state.value.searching)
        coVerify { logAction("SOCIAL_SEARCH", caseId = 10L, details = any()) }
    }

    @Test
    fun `promoteToLead creates lead and marks result`() = runTest {
        val result = SocialSearchResult(
            caseId = 10, platform = "VK", profileName = "Иван Иванов",
            profileUrl = "https://vk.com/ivanov", handle = "ivanov",
            snippet = null, avatarUrl = null,
            matchType = SocialMatchType.HANDLE_EXACT, relevanceScore = 0.8f,
        )
        coEvery { searchSocial(any(), any(), any()) } returns listOf(result)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.runSearch()
        advanceUntilIdle()

        vm.promoteToLead(vm.state.value.results[0])
        advanceUntilIdle()

        coVerify { addLead(match { it.type.name == "SOCIAL" }) }
        assertEquals(SearchResultStatus.PROMOTED_TO_LEAD, vm.state.value.results[0].status)
    }

    @Test
    fun `dismissResult marks result as dismissed`() = runTest {
        val result = SocialSearchResult(
            caseId = 10, platform = "VK", profileName = "Wrong Person",
            profileUrl = "https://vk.com/wrong", handle = "wrong",
            snippet = null, avatarUrl = null,
            matchType = SocialMatchType.NAME_PARTIAL, relevanceScore = 0.2f,
        )
        coEvery { searchSocial(any(), any(), any()) } returns listOf(result)

        val vm = createViewModel()
        advanceUntilIdle()
        vm.runSearch()
        advanceUntilIdle()

        vm.dismissResult(vm.state.value.results[0])

        assertEquals(SearchResultStatus.DISMISSED, vm.state.value.results[0].status)
    }

    @Test
    fun `selectPlatform filters results`() = runTest {
        val results = listOf(
            SocialSearchResult(
                caseId = 10, platform = "VK", profileName = "User1",
                profileUrl = "https://vk.com/1", handle = "u1",
                snippet = null, avatarUrl = null,
                matchType = SocialMatchType.NAME_EXACT, relevanceScore = 0.5f,
            ),
            SocialSearchResult(
                caseId = 10, platform = "OK", profileName = "User2",
                profileUrl = "https://ok.ru/2", handle = "u2",
                snippet = null, avatarUrl = null,
                matchType = SocialMatchType.NAME_EXACT, relevanceScore = 0.5f,
            ),
        )
        coEvery { searchSocial(any(), any(), any()) } returns results

        val vm = createViewModel()
        advanceUntilIdle()
        vm.runSearch()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.filteredResults.size)

        vm.selectPlatform("VK")
        assertEquals(1, vm.state.value.filteredResults.size)
        assertEquals("VK", vm.state.value.filteredResults[0].platform)

        vm.selectPlatform(null)
        assertEquals(2, vm.state.value.filteredResults.size)
    }

    @Test
    fun `missing case shows error`() = runTest {
        coEvery { getCase(10L) } returns null
        val vm = SocialSearchViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getCase, getProfile, getSocialSources,
            buildIdentityPack, searchSocial,
            addLead, logAction,
        )
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.error)
    }
}
