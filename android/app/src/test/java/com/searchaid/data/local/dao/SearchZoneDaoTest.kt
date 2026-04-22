package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.SearchZoneEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchZoneDaoTest : RoomTestBase() {

    private lateinit var dao: SearchZoneDao

    @Before
    fun setUp() {
        dao = db.searchZoneDao()
    }

    private fun entity(
        id: Long = 0,
        caseId: Long = 1L,
        score: Float = 0.5f,
        checked: Boolean = false,
    ) = SearchZoneEntity(
        id = id, caseId = caseId, lat = 55.75, lon = 37.61,
        radiusMeters = 500.0, score = score, reason = "Test zone",
        checked = checked,
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
    fun `observeByCase ordered by score desc`() = runTest {
        dao.insert(entity(caseId = 1L, score = 0.3f))
        dao.insert(entity(caseId = 1L, score = 0.9f))
        dao.insert(entity(caseId = 1L, score = 0.6f))

        dao.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(0.9f, list[0].score, 0.001f)
            assertEquals(0.6f, list[1].score, 0.001f)
            assertEquals(0.3f, list[2].score, 0.001f)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `markChecked updates zone`() = runTest {
        val id = dao.insert(entity(checked = false))

        dao.markChecked(id)

        dao.observeByCase(1L).test {
            assertTrue(awaitItem()[0].checked)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase reacts to inserts`() = runTest {
        dao.observeByCase(1L).test {
            assertEquals(0, awaitItem().size)

            dao.insert(entity(caseId = 1L))
            assertEquals(1, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }
}
