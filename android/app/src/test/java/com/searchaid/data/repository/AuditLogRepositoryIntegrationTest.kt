package com.searchaid.data.repository

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.domain.model.AuditLogEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuditLogRepositoryIntegrationTest : RoomTestBase() {

    private lateinit var repo: AuditLogRepositoryImpl

    @Before
    fun setUp() {
        repo = AuditLogRepositoryImpl(db.auditLogDao())
    }

    private fun entry(
        caseId: Long? = 1L,
        action: String = "CASE_STARTED",
        timestamp: Long = 1000L,
    ) = AuditLogEntry(
        caseId = caseId, action = action,
        details = "Test details", operatorId = "op1", timestamp = timestamp,
    )

    @Test
    fun `log returns generated id`() = runTest {
        val id = repo.log(entry())
        assertEquals(1L, id)
    }

    @Test
    fun `log and observeByCase round-trip`() = runTest {
        repo.log(entry(caseId = 1L, action = "A"))
        repo.log(entry(caseId = 2L, action = "B"))
        repo.log(entry(caseId = 1L, action = "C"))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals(1L, it.caseId) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeAll returns domain models`() = runTest {
        repo.log(entry(caseId = 1L, action = "A", timestamp = 100))
        repo.log(entry(caseId = null, action = "B", timestamp = 300))

        repo.observeAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            // Ordered by timestamp desc
            assertEquals("B", list[0].action)
            assertEquals("A", list[1].action)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase ordered by timestamp desc`() = runTest {
        repo.log(entry(caseId = 1L, timestamp = 100))
        repo.log(entry(caseId = 1L, timestamp = 300))
        repo.log(entry(caseId = 1L, timestamp = 200))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(300L, list[0].timestamp)
            assertEquals(200L, list[1].timestamp)
            assertEquals(100L, list[2].timestamp)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `entries with null caseId appear in observeAll but not observeByCase`() = runTest {
        repo.log(entry(caseId = null, action = "GLOBAL"))
        repo.log(entry(caseId = 1L, action = "CASE"))

        repo.observeAll().test {
            assertEquals(2, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("CASE", list[0].action)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeAll reacts to new log entries`() = runTest {
        repo.observeAll().test {
            assertEquals(0, awaitItem().size)

            repo.log(entry(action = "FIRST"))
            assertEquals(1, awaitItem().size)

            repo.log(entry(action = "SECOND"))
            assertEquals(2, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }
}
