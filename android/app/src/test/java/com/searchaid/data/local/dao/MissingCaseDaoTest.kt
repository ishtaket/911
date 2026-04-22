package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.MissingCaseEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MissingCaseDaoTest : RoomTestBase() {

    private lateinit var dao: MissingCaseDao

    @Before
    fun setUp() {
        dao = db.missingCaseDao()
    }

    private fun entity(
        id: Long = 0,
        personId: Long = 1L,
        status: String = "ACTIVE",
        createdAt: Long = 1000L,
    ) = MissingCaseEntity(
        id = id, personId = personId, status = status, createdAt = createdAt,
        lastSeenTime = 900L, lastSeenLocationName = "Central Park",
        lastSeenLat = 55.75, lastSeenLon = 37.61,
        clothesDescription = "Blue jacket", notes = null, operatorId = null,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `getById returns inserted case`() = runTest {
        val id = dao.insert(entity())
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals("ACTIVE", result!!.status)
        assertEquals(55.75, result.lastSeenLat!!, 0.001)
    }

    @Test
    fun `getById returns null for missing id`() = runTest {
        assertNull(dao.getById(999))
    }

    @Test
    fun `observeActive only returns ACTIVE cases`() = runTest {
        dao.insert(entity(status = "ACTIVE", createdAt = 200))
        dao.insert(entity(status = "FOUND", createdAt = 300))
        dao.insert(entity(status = "CLOSED", createdAt = 100))
        dao.insert(entity(status = "ACTIVE", createdAt = 400))

        dao.observeActive().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals("ACTIVE", it.status) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeActive ordered by createdAt desc`() = runTest {
        dao.insert(entity(status = "ACTIVE", createdAt = 100))
        dao.insert(entity(status = "ACTIVE", createdAt = 300))

        dao.observeActive().test {
            val list = awaitItem()
            assertEquals(300L, list[0].createdAt)
            assertEquals(100L, list[1].createdAt)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByPerson filters by personId`() = runTest {
        dao.insert(entity(personId = 1L))
        dao.insert(entity(personId = 2L))
        dao.insert(entity(personId = 1L))

        dao.observeByPerson(1L).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals(1L, it.personId) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `update modifies case status`() = runTest {
        val id = dao.insert(entity(status = "ACTIVE"))
        val original = dao.getById(id)!!
        dao.update(original.copy(status = "FOUND"))

        val updated = dao.getById(id)!!
        assertEquals("FOUND", updated.status)
    }

    @Test
    fun `observeActive reacts to status change`() = runTest {
        dao.observeActive().test {
            assertEquals(0, awaitItem().size)

            val id = dao.insert(entity(status = "ACTIVE"))
            assertEquals(1, awaitItem().size)

            val case = dao.getById(id)!!
            dao.update(case.copy(status = "FOUND"))
            assertEquals(0, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }
}
