package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.SocialSourceEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SocialSourceDaoTest : RoomTestBase() {

    private lateinit var dao: SocialSourceDao

    @Before
    fun setUp() {
        dao = db.socialSourceDao()
    }

    private fun entity(
        id: Long = 0,
        personId: Long = 1L,
        platform: String = "Telegram",
    ) = SocialSourceEntity(
        id = id, personId = personId, platform = platform,
        sourceType = "messenger", title = "Main account",
        handleOrAlias = "@user", region = "Moscow", url = null,
        visibility = "public", enabled = true,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `getById returns inserted source`() = runTest {
        val id = dao.insert(entity())
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals("Telegram", result!!.platform)
    }

    @Test
    fun `getById returns null for missing id`() = runTest {
        assertNull(dao.getById(999))
    }

    @Test
    fun `observeByPerson filters by personId`() = runTest {
        dao.insert(entity(personId = 1L))
        dao.insert(entity(personId = 2L))
        dao.insert(entity(personId = 1L, platform = "VK"))

        dao.observeByPerson(1L).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            list.forEach { assertEquals(1L, it.personId) }
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByPerson ordered by platform asc`() = runTest {
        dao.insert(entity(personId = 1L, platform = "VK"))
        dao.insert(entity(personId = 1L, platform = "Instagram"))
        dao.insert(entity(personId = 1L, platform = "Telegram"))

        dao.observeByPerson(1L).test {
            val list = awaitItem()
            assertEquals("Instagram", list[0].platform)
            assertEquals("Telegram", list[1].platform)
            assertEquals("VK", list[2].platform)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `deleteById removes source`() = runTest {
        val id = dao.insert(entity())
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }
}
