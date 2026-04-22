package com.searchaid.ui.feature_witness

import androidx.lifecycle.SavedStateHandle
import com.searchaid.MainDispatcherRule
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.usecase.AddWitnessReportUseCase
import com.searchaid.domain.usecase.GetWitnessReportsByCaseUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdateWitnessReportStatusUseCase
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
class WitnessReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getReports = mockk<GetWitnessReportsByCaseUseCase>()
    private val addReport = mockk<AddWitnessReportUseCase>(relaxed = true)
    private val updateReportStatus = mockk<UpdateWitnessReportStatusUseCase>(relaxed = true)
    private val logAction = mockk<LogActionUseCase>(relaxed = true)

    private val testReport = WitnessReport(
        id = 1, caseId = 10, sourceName = "John", sourceType = "Eyewitness",
        text = "Saw person near river", timestamp = 1000L,
        possibleLocationName = "River bank", lat = 55.75, lon = 37.61,
        confidence = 0.7f, status = ReportStatus.NEW,
    )

    private fun createViewModel(): WitnessReportsViewModel {
        every { getReports(10L) } returns flowOf(listOf(testReport))
        return WitnessReportsViewModel(
            SavedStateHandle(mapOf("caseId" to 10L)),
            getReports, addReport, updateReportStatus, logAction,
        )
    }

    @Test
    fun `init loads reports from repository`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.reports.size)
        assertEquals("Saw person near river", vm.state.value.reports[0].text)
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
    fun `submitReport calls addReport and logs action`() = runTest {
        coEvery { addReport(any()) } returns 5L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        vm.onSourceNameChange("Anna")
        vm.onSourceTypeChange("Phone call")
        vm.onTextChange("Person was walking east on Main St")
        vm.submitReport()
        advanceUntilIdle()

        coVerify { addReport(match { it.sourceName == "Anna" && it.text == "Person was walking east on Main St" }) }
        coVerify { logAction("REPORT_ADDED", caseId = 10L, details = any()) }
        assertFalse(vm.state.value.showAddDialog)
    }

    @Test
    fun `verifyReport updates status and logs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.verifyReport(1L)
        advanceUntilIdle()

        coVerify { updateReportStatus(1L, ReportStatus.VERIFIED) }
        coVerify { logAction("REPORT_VERIFIED", caseId = 10L, details = any()) }
    }

    @Test
    fun `rejectReport updates status and logs`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.rejectReport(1L)
        advanceUntilIdle()

        coVerify { updateReportStatus(1L, ReportStatus.REJECTED) }
        coVerify { logAction("REPORT_REJECTED", caseId = 10L, details = any()) }
    }

    @Test
    fun `blank fields stored as null in submitted report`() = runTest {
        coEvery { addReport(any()) } returns 1L
        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        vm.onSourceNameChange("   ")
        vm.onSourceTypeChange("")
        vm.onTextChange("Some text")
        vm.submitReport()
        advanceUntilIdle()

        coVerify {
            addReport(match {
                it.sourceName == null && it.sourceType == null && it.text == "Some text"
            })
        }
    }
}
