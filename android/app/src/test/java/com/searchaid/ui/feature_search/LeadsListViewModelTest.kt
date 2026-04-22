package com.searchaid.ui.feature_search

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.usecase.AddLeadUseCase
import com.searchaid.domain.usecase.GetLeadsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdateLeadStatusUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeadsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getLeads = mockk<GetLeadsByCaseUseCase>()
    private val addLead = mockk<AddLeadUseCase>(relaxed = true)
    private val updateLeadStatus = mockk<UpdateLeadStatusUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testLead = SearchLead(
        id = 1, caseId = 10, type = LeadType.MANUAL,
        platform = "Telegram", matchedValue = "user123",
        textSnippet = "Seen near park", possibleLocationName = "Park",
        lat = 55.75, lon = 37.61, timestamp = 1000L,
        confidence = 0.8f, status = LeadStatus.NEW,
    )

    private fun createViewModel(): LeadsListViewModel {
        every { getLeads(10L) } returns flowOf(listOf(testLead))
        return LeadsListViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getLeads, addLead, updateLeadStatus, logAction,
        )
    }

    @Test
    fun `init loads leads from repository`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.leads.size)
        assertEquals("user123", vm.state.value.leads[0].matchedValue)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `showAddDialog opens dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()

        assertTrue(vm.state.value.showAddDialog)
    }

    @Test
    fun `submitLead calls addLead and logs action`() = runTest {
        coEvery { addLead(any()) } returns 5L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        vm.onTypeChange(LeadType.WITNESS)
        vm.onPlatformChange("VK")
        vm.onMatchedValueChange("witness_report")
        vm.onTextSnippetChange("Saw person near river")
        vm.submitLead()
        advanceUntilIdle()

        coVerify { addLead(match { it.type == LeadType.WITNESS && it.platform == "VK" }) }
        coVerify { logAction("LEAD_ADDED", caseId = 10L, details = any()) }
        assertFalse(vm.state.value.showAddDialog)
    }

    @Test
    fun `confirmLead updates status and logs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.confirmLead(1L)
        advanceUntilIdle()

        coVerify { updateLeadStatus(1L, LeadStatus.CONFIRMED) }
        coVerify { logAction("LEAD_CONFIRMED", caseId = 10L, details = any()) }
    }

    @Test
    fun `rejectLead updates status and logs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.rejectLead(1L)
        advanceUntilIdle()

        coVerify { updateLeadStatus(1L, LeadStatus.REJECTED) }
        coVerify { logAction("LEAD_REJECTED", caseId = 10L, details = any()) }
    }

    @Test
    fun `blank fields stored as null in submitted lead`() = runTest {
        coEvery { addLead(any()) } returns 1L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        vm.onPlatformChange("   ")
        vm.onMatchedValueChange("")
        vm.submitLead()
        advanceUntilIdle()

        coVerify {
            addLead(match {
                it.platform == null && it.matchedValue == null
            })
        }
    }
}
