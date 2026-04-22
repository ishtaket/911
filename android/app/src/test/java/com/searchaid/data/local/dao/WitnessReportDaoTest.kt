package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.WitnessReportEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WitnessReportDaoTest : RoomTestBase() {

    private lateinit var dao: WitnessReportDao

    @Before
    fun setUp() {
        dao = db.witnessReportDao()
    }

    private fun entity(
        id: Long = 0,
        caseId: Long = 1L,
        timestamp: Long = 1000L,
        status: String = "NEW",
    ) = WitnessReportEntity(
        id = id, caseId = caseId, sourceName = "John",
        sourceType = "Eyewitness", text = "Saw person near river",
        timestamp = timestamp, possibleLocationName = "River bank",
        lat = 55.75, lon = 37.61, confidence = 0.7f, status = status,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `getById returns inserted report`() = runTest {
        val id = dao.insert(entity())
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals("John", result!!.sourceName)
        assertEquals("Saw person near river", result.text)
    }

    @Test
    fun `getById returns null for missing id`() = runTest {
        assertNull(dao.getById(999))
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
        dao.insert(entity(caseId = 1L, timestamp = 1000L))
        dao.insert(entity(caseId = 1L, timestamp = 3000L))
        dao.insert(entity(caseId = 1L, timestamp = 2000L))

        dao.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(3000L, list[0].timestamp)
            assertEquals(2000L, list[1].timestamp)
            assertEquals(1000L, list[2].timestamp)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `update modifies report status`() = runTest {
        val id = dao.insert(entity(status = "NEW"))
        val original = dao.getById(id)!!
        dao.update(original.copy(status = "VERIFIED"))

        val updated = dao.getById(id)!!
        assertEquals("VERIFIED", updated.status)
    }

    @Test
    fun `observeByCase reacts to updates`() = runTest {
        dao.observeByCase(1L).test {
            assertEquals(0, awaitItem().size)

            dao.insert(entity(caseId = 1L))
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("NEW", list[0].status)

            dao.update(list[0].copy(status = "VERIFIED"))
            assertEquals("VERIFIED", awaitItem()[0].status)

            cancelAndConsumeRemainingEvents()
        }
    }
}
