package com.searchaid.data.local.dao

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.data.local.entity.PersonProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PersonProfileDaoTest : RoomTestBase() {

    private lateinit var dao: PersonProfileDao

    @Before
    fun setUp() {
        dao = db.personProfileDao()
    }

    private fun entity(
        id: Long = 0,
        name: String = "Ivan",
        updatedAt: Long = 1000L,
    ) = PersonProfileEntity(
        id = id, name = name, age = 78, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = "Scar on left hand",
        habits = "Walks to park", knownLocations = "Central Park",
        aliases = emptyList(), nicknames = listOf("Vanya"),
        emails = emptyList(), phones = listOf("+71234567890"),
        familyNotes = null, createdAt = 1000L, updatedAt = updatedAt,
    )

    @Test
    fun `insert returns generated id`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1L, id)
    }

    @Test
    fun `getById returns inserted profile`() = runTest {
        val id = dao.insert(entity())
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals("Ivan", result!!.name)
        assertEquals(78, result.age)
        assertEquals(listOf("Vanya"), result.nicknames)
    }

    @Test
    fun `getById returns null for missing id`() = runTest {
        assertNull(dao.getById(999))
    }

    @Test
    fun `observeAll emits profiles ordered by updatedAt desc`() = runTest {
        dao.insert(entity(name = "First", updatedAt = 100))
        dao.insert(entity(name = "Second", updatedAt = 300))
        dao.insert(entity(name = "Third", updatedAt = 200))

        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertEquals("Second", list[0].name)
            assertEquals("Third", list[1].name)
            assertEquals("First", list[2].name)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `update modifies existing profile`() = runTest {
        val id = dao.insert(entity())
        val original = dao.getById(id)!!
        dao.update(original.copy(name = "Updated", age = 80))

        val updated = dao.getById(id)!!
        assertEquals("Updated", updated.name)
        assertEquals(80, updated.age)
    }

    @Test
    fun `deleteById removes profile`() = runTest {
        val id = dao.insert(entity())
        dao.deleteById(id)
        assertNull(dao.getById(id))
    }

    @Test
    fun `observeAll reacts to insertions`() = runTest {
        dao.observeAll().test {
            assertEquals(0, awaitItem().size)

            dao.insert(entity(name = "New"))
            assertEquals(1, awaitItem().size)

            cancelAndConsumeRemainingEvents()
        }
    }
}
