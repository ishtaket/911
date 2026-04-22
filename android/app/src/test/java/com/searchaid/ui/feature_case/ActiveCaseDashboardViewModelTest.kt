package com.searchaid.ui.feature_case

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.GetMissingCaseUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdateCaseStatusUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveCaseDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCase = mockk<GetMissingCaseUseCase>(relaxed = true)
    private val getProfile = mockk<GetPersonProfileUseCase>(relaxed = true)
    private val updateStatus = mockk<UpdateCaseStatusUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testCase = MissingCase(
        id = 10, personId = 42, status = CaseStatus.ACTIVE, createdAt = 1000,
        lastSeenTime = 900, lastSeenLocationName = "Park",
        lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = null, operatorId = null,
    )

    private val testProfile = PersonProfile(
        id = 42, name = "Ivan", age = 78, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = null,
        habits = null, knownLocations = null,
        aliases = emptyList(), nicknames = emptyList(),
        emails = emptyList(), phones = emptyList(),
        familyNotes = null, createdAt = 1000, updatedAt = 2000,
    )

    private fun createViewModel() = ActiveCaseDashboardViewModel(
        SavedStateHandle(mapOf("caseId" to 10L)),
        getCase, getProfile, updateStatus, logAction,
    )

    @Test
    fun `init loads case and person`() = runTest {
        coEvery { getCase(10) } returns testCase
        coEvery { getProfile(42) } returns testProfile
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(testCase, vm.state.value.case_)
        assertEquals(testProfile, vm.state.value.person)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun `markFound updates status and logs`() = runTest {
        coEvery { getCase(10) } returns testCase
        coEvery { getProfile(42) } returns testProfile
        val vm = createViewModel()
        advanceUntilIdle()

        vm.markFound()
        advanceUntilIdle()

        coVerify { updateStatus(10, CaseStatus.FOUND) }
        coVerify { logAction("CASE_FOUND", caseId = 10L, details = any(), operatorId = null) }
        assertEquals(CaseStatus.FOUND, vm.state.value.case_?.status)
    }

    @Test
    fun `closeCase updates status and logs`() = runTest {
        coEvery { getCase(10) } returns testCase
        coEvery { getProfile(42) } returns testProfile
        val vm = createViewModel()
        advanceUntilIdle()

        vm.closeCase()
        advanceUntilIdle()

        coVerify { updateStatus(10, CaseStatus.CLOSED) }
        coVerify { logAction("CASE_CLOSED", caseId = 10L, details = any(), operatorId = null) }
        assertEquals(CaseStatus.CLOSED, vm.state.value.case_?.status)
    }
}
