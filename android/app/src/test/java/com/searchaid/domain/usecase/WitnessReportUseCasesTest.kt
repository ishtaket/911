package com.searchaid.domain.usecase

import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.repository.WitnessReportRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WitnessReportUseCasesTest {

    private val repository = mockk<WitnessReportRepository>(relaxed = true)

    private val testReport = WitnessReport(
        id = 1,
        caseId = 10,
        sourceName = "John",
        sourceType = "Eyewitness",
        text = "Saw person near river",
        timestamp = 1000L,
        possibleLocationName = "River bank",
        lat = 55.75,
        lon = 37.61,
        confidence = 0.7f,
        status = ReportStatus.NEW,
    )

    @Test
    fun `AddWitnessReport delegates to repository`() = runTest {
        coEvery { repository.add(testReport) } returns 1L
        val useCase = AddWitnessReportUseCase(repository)

        val id = useCase(testReport)

        assertEquals(1L, id)
        coVerify { repository.add(testReport) }
    }

    @Test
    fun `GetWitnessReportsByCase returns flow from repository`() = runTest {
        coEvery { repository.observeByCase(10) } returns flowOf(listOf(testReport))
        val useCase = GetWitnessReportsByCaseUseCase(repository)

        val result = useCase(10).first()

        assertEquals(1, result.size)
        assertEquals("Saw person near river", result[0].text)
    }

    @Test
    fun `GetWitnessReportsByCase returns empty list when no reports`() = runTest {
        coEvery { repository.observeByCase(99) } returns flowOf(emptyList())
        val useCase = GetWitnessReportsByCaseUseCase(repository)

        val result = useCase(99).first()

        assertEquals(0, result.size)
    }

    @Test
    fun `UpdateWitnessReportStatus delegates with VERIFIED`() = runTest {
        val useCase = UpdateWitnessReportStatusUseCase(repository)

        useCase(1L, ReportStatus.VERIFIED)

        coVerify { repository.updateStatus(1L, ReportStatus.VERIFIED) }
    }

    @Test
    fun `UpdateWitnessReportStatus delegates with REJECTED`() = runTest {
        val useCase = UpdateWitnessReportStatusUseCase(repository)

        useCase(1L, ReportStatus.REJECTED)

        coVerify { repository.updateStatus(1L, ReportStatus.REJECTED) }
    }
}
