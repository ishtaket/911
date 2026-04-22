package com.searchaid.data.repository

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SearchLeadRepositoryIntegrationTest : RoomTestBase() {

    private lateinit var repo: SearchLeadRepositoryImpl

    @Before
    fun setUp() {
        repo = SearchLeadRepositoryImpl(db.searchLeadDao())
    }

    private fun lead(
        caseId: Long = 1L,
        confidence: Float = 0.5f,
        status: LeadStatus = LeadStatus.NEW,
        type: LeadType = LeadType.MANUAL,
    ) = SearchLead(
        caseId = caseId, type = type, platform = null,
        matchedValue = null, textSnippet = "Seen near store",
        possibleLocationName = "Store", lat = 55.75, lon = 37.61,
        timestamp = 1000L, confidence = confidence, status = status,
    )

    @Test
    fun `add and observeByCase round-trip`() = runTest {
        repo.add(lead(caseId = 1L, confidence = 0.8f))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(LeadType.MANUAL, list[0].type)
            assertEquals(0.8f, list[0].confidence, 0.001f)
            assertEquals(LeadStatus.NEW, list[0].status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateStatus transitions lead status`() = runTest {
        val id = repo.add(lead(status = LeadStatus.NEW))

        repo.updateStatus(id, LeadStatus.CONFIRMED)

        repo.observeByCase(1L).test {
            assertEquals(LeadStatus.CONFIRMED, awaitItem()[0].status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateStatus to REJECTED`() = runTest {
        val id = repo.add(lead())
        repo.updateStatus(id, LeadStatus.REJECTED)

        repo.observeByCase(1L).test {
            assertEquals(LeadStatus.REJECTED, awaitItem()[0].status)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateStatus on non-existent id does nothing`() = runTest {
        repo.updateStatus(999, LeadStatus.CONFIRMED) // should not throw
    }

    @Test
    fun `observeByCase returns leads ordered by confidence desc`() = runTest {
        repo.add(lead(caseId = 1L, confidence = 0.3f))
        repo.add(lead(caseId = 1L, confidence = 0.9f))
        repo.add(lead(caseId = 1L, confidence = 0.6f))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(0.9f, list[0].confidence, 0.001f)
            assertEquals(0.6f, list[1].confidence, 0.001f)
            assertEquals(0.3f, list[2].confidence, 0.001f)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase filters by caseId`() = runTest {
        repo.add(lead(caseId = 1L))
        repo.add(lead(caseId = 2L))

        repo.observeByCase(1L).test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `different lead types stored correctly`() = runTest {
        repo.add(lead(type = LeadType.WEB))
        repo.add(lead(type = LeadType.SOCIAL))
        repo.add(lead(type = LeadType.WITNESS))

        repo.observeByCase(1L).test {
            val types = awaitItem().map { it.type }.toSet()
            assertEquals(setOf(LeadType.WEB, LeadType.SOCIAL, LeadType.WITNESS), types)
            cancelAndConsumeRemainingEvents()
        }
    }
}
