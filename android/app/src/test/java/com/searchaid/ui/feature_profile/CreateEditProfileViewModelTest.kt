package com.searchaid.ui.feature_profile

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.CreatePersonProfileUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdatePersonProfileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEditProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getProfile = mockk<GetPersonProfileUseCase>(relaxed = true)
    private val createProfile = mockk<CreatePersonProfileUseCase>(relaxed = true)
    private val updateProfile = mockk<UpdatePersonProfileUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private fun createViewModel(profileId: Long = -1L) = CreateEditProfileViewModel(
        SavedStateHandle(mapOf("profileId" to profileId)),
        getProfile, createProfile, updateProfile, logAction,
    )

    @Test
    fun `new profile mode starts with empty form`() {
        val vm = createViewModel()
        val state = vm.state.value

        assertEquals("", state.name)
        assertFalse(state.isEdit)
        assertFalse(state.loading)
    }

    @Test
    fun `save with blank name does nothing`() = runTest {
        val vm = createViewModel()

        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { createProfile(any()) }
    }

    @Test
    fun `save creates profile and logs action`() = runTest {
        coEvery { createProfile(any()) } returns 5L
        val vm = createViewModel()

        vm.onNameChange("Ivan Petrov")
        vm.onAgeChange("78")
        vm.onConditionChange("Alzheimer's")
        vm.onAliasesChange("Vanya, Ivan P.")
        vm.save()
        advanceUntilIdle()

        coVerify { createProfile(match { it.name == "Ivan Petrov" && it.age == 78 }) }
        coVerify { logAction("PROFILE_CREATED", caseId = null, details = any(), operatorId = null) }
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `save with invalid age treats as null`() = runTest {
        coEvery { createProfile(any()) } returns 1L
        val vm = createViewModel()

        vm.onNameChange("Test")
        vm.onAgeChange("abc")
        vm.save()
        advanceUntilIdle()

        coVerify { createProfile(match { it.age == null }) }
    }

    @Test
    fun `edit mode loads existing profile`() = runTest {
        val existing = PersonProfile(
            id = 5, name = "Existing", age = 65, photoUri = null,
            condition = "Dementia", distinguishingFeatures = null,
            habits = "walks", knownLocations = null,
            aliases = listOf("Ali"), nicknames = emptyList(),
            emails = listOf("a@b.com"), phones = emptyList(),
            familyNotes = null, createdAt = 1000, updatedAt = 2000,
        )
        coEvery { getProfile(5) } returns existing
        val vm = createViewModel(profileId = 5)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isEdit)
        assertEquals("Existing", state.name)
        assertEquals("65", state.age)
        assertEquals("Dementia", state.condition)
        assertEquals("Ali", state.aliases)
        assertEquals("a@b.com", state.emails)
    }

    @Test
    fun `edit mode save calls update and logs`() = runTest {
        val existing = PersonProfile(
            id = 5, name = "Existing", age = 65, photoUri = null,
            condition = null, distinguishingFeatures = null,
            habits = null, knownLocations = null,
            aliases = emptyList(), nicknames = emptyList(),
            emails = emptyList(), phones = emptyList(),
            familyNotes = null, createdAt = 1000, updatedAt = 2000,
        )
        coEvery { getProfile(5) } returns existing
        val vm = createViewModel(profileId = 5)
        advanceUntilIdle()

        vm.onNameChange("Updated Name")
        vm.save()
        advanceUntilIdle()

        coVerify { updateProfile(match { it.name == "Updated Name" && it.id == 5L }) }
        coVerify { logAction("PROFILE_UPDATED", caseId = null, details = any(), operatorId = null) }
    }

    @Test
    fun `aliases are split by comma and trimmed`() = runTest {
        coEvery { createProfile(any()) } returns 1L
        val vm = createViewModel()

        vm.onNameChange("Test")
        vm.onAliasesChange("  alice , bob ,  charlie  ")
        vm.save()
        advanceUntilIdle()

        coVerify {
            createProfile(match {
                it.aliases == listOf("alice", "bob", "charlie")
            })
        }
    }
}
