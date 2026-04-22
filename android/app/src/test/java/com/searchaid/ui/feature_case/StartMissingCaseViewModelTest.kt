package com.searchaid.ui.feature_case

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.StartMissingCaseUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartMissingCaseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getProfile = mockk<GetPersonProfileUseCase>(relaxed = true)
    private val startCase = mockk<StartMissingCaseUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testProfile = PersonProfile(
        id = 42, name = "Ivan", age = 78, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = null,
        habits = null, knownLocations = null,
        aliases = emptyList(), nicknames = emptyList(),
        emails = emptyList(), phones = emptyList(),
        familyNotes = null, createdAt = 1000, updatedAt = 2000,
    )

    private fun createViewModel() = StartMissingCaseViewModel(
        SavedStateHandle(mapOf("profileId" to 42L)),
        getProfile, startCase, logAction,
    )

    @Test
    fun `init loads person profile`() = runTest {
        coEvery { getProfile(42) } returns testProfile
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(testProfile, vm.state.value.person)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun `submit creates case with form data`() = runTest {
        coEvery { getProfile(42) } returns testProfile
        coEvery { startCase(any()) } returns 10L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLocationNameChange("Park entrance")
        vm.onLatChange("55.75")
        vm.onLonChange("37.61")
        vm.onClothesChange("Blue jacket")
        vm.onNotesChange("Left without phone")
        vm.submit()
        advanceUntilIdle()

        coVerify {
            startCase(match {
                it.personId == 42L &&
                it.lastSeenLocationName == "Park entrance" &&
                it.lastSeenLat == 55.75 &&
                it.lastSeenLon == 37.61 &&
                it.clothesDescription == "Blue jacket" &&
                it.notes == "Left without phone"
            })
        }

        assertEquals(10L, vm.state.value.createdCaseId)
    }

    @Test
    fun `submit logs CASE_STARTED action`() = runTest {
        coEvery { getProfile(42) } returns testProfile
        coEvery { startCase(any()) } returns 10L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLocationNameChange("Park")
        vm.submit()
        advanceUntilIdle()

        coVerify { logAction("CASE_STARTED", caseId = 10L, details = any(), operatorId = null) }
    }

    @Test
    fun `submit without profile does nothing`() = runTest {
        coEvery { getProfile(42) } returns null
        val vm = createViewModel()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { startCase(any()) }
        assertNull(vm.state.value.createdCaseId)
    }

    @Test
    fun `blank fields are stored as null`() = runTest {
        coEvery { getProfile(42) } returns testProfile
        coEvery { startCase(any()) } returns 1L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onLocationNameChange("   ")
        vm.onClothesChange("")
        vm.submit()
        advanceUntilIdle()

        coVerify {
            startCase(match {
                it.lastSeenLocationName == null &&
                it.clothesDescription == null
            })
        }
    }
}
