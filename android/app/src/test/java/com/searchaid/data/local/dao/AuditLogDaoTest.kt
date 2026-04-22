package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.AuditLogEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuditLogDaoTest : RoomTestBase() {

    private lateinit var dao: AuditLogDao

    @Before
    fun setUp() {
        dao = db.auditLogDao()
    }

    private fun entity(
        id: Long = 0,
        caseId: Long? = 1L,
        action: String = "CASE_STARTED",
        timestamp: Long = 1000L,
    ) = AuditLogEntryEntity(
        id = id, caseId = caseId, action = action,
        details = "Test details", operatorId = "op1", timestamp = timestamp,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `observeByCase filters by caseId`() = runTest {
        dao.insert(entity(caseId = 1L))
        dao.insert(entity(caseId = 2L))
        dao.insert(entity(caseId = 1L))

        dao.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals(1L, it.caseId) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase ordered by timestamp desc`() = runTest {
        dao.insert(entity(caseId = 1L, timestamp = 100))
        dao.insert(entity(caseId = 1L, timestamp = 300))
        dao.insert(entity(caseId = 1L, timestamp = 200))

        dao.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(300L, list[0].timestamp)
            assertEquals(200L, list[1].timestamp)
            assertEquals(100L, list[2].timestamp)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeAll returns all entries ordered by timestamp desc`() = runTest {
        dao.insert(entity(caseId = 1L, action = "A", timestamp = 100))
        dao.insert(entity(caseId = 2L, action = "B", timestamp = 300))
        dao.insert(entity(caseId = null, action = "C", timestamp = 200))

        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertEquals("B", list[0].action)
            assertEquals("C", list[1].action)
            assertEquals("A", list[2].action)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeAll reacts to new entries`() = runTest {
        dao.observeAll().test {
            assertEquals(0, awaitItem().size)

            dao.insert(entity(action = "FIRST"))
            assertEquals(1, awaitItem().size)

            dao.insert(entity(action = "SECOND"))
            assertEquals(2, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `entries with null caseId are stored correctly`() = runTest {
        dao.insert(entity(caseId = null, action = "GLOBAL_ACTION"))

        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(null, list[0].caseId)
            assertEquals("GLOBAL_ACTION", list[0].action)
            cancelAndConsumeRemainingEvents()
        }
    }
}
