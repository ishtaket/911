package com.searchaid.data.repository

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WitnessReportRepositoryIntegrationTest : RoomTestBase() {

    private lateinit var repo: WitnessReportRepositoryImpl

    @Before
    fun setUp() {
        repo = WitnessReportRepositoryImpl(db.witnessReportDao())
    }

    private fun report(
        caseId: Long = 1L,
        timestamp: Long = 1000L,
        status: ReportStatus = ReportStatus.NEW,
    ) = WitnessReport(
        caseId = caseId, sourceName = "John", sourceType = "Eyewitness",
        text = "Saw person near river", timestamp = timestamp,
        possibleLocationName = "River bank", lat = 55.75, lon = 37.61,
        confidence = 0.7f, status = status,
    )

    @Test
    fun `add and observeByCase round-trip`() = runTest {
        repo.add(report(caseId = 1L))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("John", list[0].sourceName)
            assertEquals("Saw person near river", list[0].text)
            assertEquals(ReportStatus.NEW, list[0].status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateStatus transitions to VERIFIED`() = runTest {
        val id = repo.add(report())

        repo.updateStatus(id, ReportStatus.VERIFIED)

        repo.observeByCase(1L).test {
            assertEquals(ReportStatus.VERIFIED, awaitItem()[0].status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateStatus transitions to REJECTED`() = runTest {
        val id = repo.add(report())
        repo.updateStatus(id, ReportStatus.REJECTED)

        repo.observeByCase(1L).test {
            assertEquals(ReportStatus.REJECTED, awaitItem()[0].status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateStatus on non-existent id does nothing`() = runTest {
        repo.updateStatus(999, ReportStatus.VERIFIED) // should not throw
    }

    @Test
    fun `observeByCase returns reports ordered by timestamp desc`() = runTest {
        repo.add(report(caseId = 1L, timestamp = 1000L))
        repo.add(report(caseId = 1L, timestamp = 3000L))
        repo.add(report(caseId = 1L, timestamp = 2000L))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(3000L, list[0].timestamp)
            assertEquals(2000L, list[1].timestamp)
            assertEquals(1000L, list[2].timestamp)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase filters by caseId`() = runTest {
        repo.add(report(caseId = 1L))
        repo.add(report(caseId = 2L))

        repo.observeByCase(1L).test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }
}
