package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.HistoricalPlaceEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoricalPlaceDaoTest : RoomTestBase() {

    private lateinit var dao: HistoricalPlaceDao

    @Before
    fun setUp() {
        dao = db.historicalPlaceDao()
    }

    private fun entity(
        id: Long = 0,
        personId: Long = 1L,
        title: String = "Home",
    ) = HistoricalPlaceEntity(
        id = id, personId = personId, title = title,
        lat = 55.75, lon = 37.61, source = "family", note = null,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `observeByPerson filters by personId`() = runTest {
        dao.insert(entity(personId = 1L, title = "Home"))
        dao.insert(entity(personId = 2L, title = "School"))
        dao.insert(entity(personId = 1L, title = "Park"))

        dao.observeByPerson(1L).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals(1L, it.personId) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `deleteById removes place`() = runTest {
        val id = dao.insert(entity(personId = 1L))

        dao.observeByPerson(1L).test {
            assertEquals(1, awaitItem().size)

            dao.deleteById(id)
            assertEquals(0, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByPerson returns empty for unknown person`() = runTest {
        dao.insert(entity(personId = 1L))

        dao.observeByPerson(999L).test {
            assertEquals(0, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }
}
