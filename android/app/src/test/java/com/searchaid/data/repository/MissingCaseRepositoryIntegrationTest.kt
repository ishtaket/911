package com.searchaid.data.repository

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class MissingCaseRepositoryIntegrationTest : RoomTestBase() {

    private lateinit var repo: MissingCaseRepositoryImpl

    @Before
    fun setUp() {
        repo = MissingCaseRepositoryImpl(db.missingCaseDao())
    }

    private fun case_(
        personId: Long = 1L,
        status: CaseStatus = CaseStatus.ACTIVE,
    ) = MissingCase(
        personId = personId, status = status,
        createdAt = System.currentTimeMillis(),
        lastSeenTime = 900L, lastSeenLocationName = "Park",
        lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = null, operatorId = null,
    )

    @Test
    fun `create and getById round-trip`() = runTest {
        val id = repo.create(case_())
        val result = repo.getById(id)
        assertNotNull(result)
        assertEquals(CaseStatus.ACTIVE, result!!.status)
        assertEquals(55.75, result.lastSeenLat!!, 0.001)
    }

    @Test
    fun `updateStatus transitions case status`() = runTest {
        val id = repo.create(case_(status = CaseStatus.ACTIVE))
        repo.updateStatus(id, CaseStatus.FOUND)

        val result = repo.getById(id)!!
        assertEquals(CaseStatus.FOUND, result.status)
    }

    @Test
    fun `updateStatus to CLOSED`() = runTest {
        val id = repo.create(case_(status = CaseStatus.ACTIVE))
        repo.updateStatus(id, CaseStatus.CLOSED)
        assertEquals(CaseStatus.CLOSED, repo.getById(id)!!.status)
    }

    @Test
    fun `updateStatus on non-existent id does nothing`() = runTest {
        repo.updateStatus(999, CaseStatus.FOUND) // should not throw
    }

    @Test
    fun `observeActive filters correctly through domain layer`() = runTest {
        repo.create(case_(status = CaseStatus.ACTIVE))
        val id2 = repo.create(case_(status = CaseStatus.ACTIVE))

        repo.observeActive().test {
            assertEquals(2, awaitItem().size)

            repo.updateStatus(id2, CaseStatus.FOUND)
            assertEquals(1, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByPerson returns domain models for specific person`() = runTest {
        repo.create(case_(personId = 1L))
        repo.create(case_(personId = 2L))
        repo.create(case_(personId = 1L))

        repo.observeByPerson(1L).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals(1L, it.personId) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `full lifecycle ACTIVE to FOUND`() = runTest {
        val id = repo.create(case_(status = CaseStatus.ACTIVE))
        assertEquals(CaseStatus.ACTIVE, repo.getById(id)!!.status)

        repo.updateStatus(id, CaseStatus.FOUND)
        assertEquals(CaseStatus.FOUND, repo.getById(id)!!.status)
    }
}
