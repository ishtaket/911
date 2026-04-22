package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.SearchLeadEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SearchLeadDaoTest : RoomTestBase() {

    private lateinit var dao: SearchLeadDao

    @Before
    fun setUp() {
        dao = db.searchLeadDao()
    }

    private fun entity(
        id: Long = 0,
        caseId: Long = 1L,
        confidence: Float = 0.5f,
        status: String = "NEW",
    ) = SearchLeadEntity(
        id = id, caseId = caseId, type = "MANUAL",
        platform = null, matchedValue = null, textSnippet = "Seen near store",
        possibleLocationName = "Store", lat = 55.75, lon = 37.61,
        timestamp = 1000L, confidence = confidence, status = status,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `getById returns inserted lead`() = runTest {
        val id = dao.insert(entity())
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals("MANUAL", result!!.type)
        assertEquals(0.5f, result.confidence, 0.001f)
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
    fun `observeByCase ordered by confidence desc`() = runTest {
        dao.insert(entity(caseId = 1L, confidence = 0.3f))
        dao.insert(entity(caseId = 1L, confidence = 0.9f))
        dao.insert(entity(caseId = 1L, confidence = 0.6f))

        dao.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(0.9f, list[0].confidence, 0.001f)
            assertEquals(0.6f, list[1].confidence, 0.001f)
            assertEquals(0.3f, list[2].confidence, 0.001f)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `update modifies lead status`() = runTest {
        val id = dao.insert(entity(status = "NEW"))
        val original = dao.getById(id)!!
        dao.update(original.copy(status = "CONFIRMED"))

        val updated = dao.getById(id)!!
        assertEquals("CONFIRMED", updated.status)
    }

    @Test
    fun `observeByCase reacts to updates`() = runTest {
        dao.observeByCase(1L).test {
            assertEquals(0, awaitItem().size)

            dao.insert(entity(caseId = 1L, confidence = 0.5f))
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("NEW", list[0].status)

            dao.update(list[0].copy(status = "CONFIRMED"))
            assertEquals("CONFIRMED", awaitItem()[0].status)

            cancelAndConsumeRemainingEvents()
        }
    }
}
