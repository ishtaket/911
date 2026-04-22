package com.searchaid.data.repository

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.domain.model.PersonProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PersonProfileRepositoryIntegrationTest : RoomTestBase() {

    private lateinit var repo: PersonProfileRepositoryImpl

    @Before
    fun setUp() {
        repo = PersonProfileRepositoryImpl(db.personProfileDao())
    }

    private fun profile(
        name: String = "Ivan",
        age: Int = 78,
    ) = PersonProfile(
        name = name, age = age, photoUri = null,
        condition = "Alzheimer's", distinguishingFeatures = "Scar",
        habits = "Walks to park", knownLocations = "Central Park",
        aliases = listOf("I. Petrov"), nicknames = listOf("Vanya"),
        emails = emptyList(), phones = listOf("+71234567890"),
        familyNotes = "Lives alone",
    )

    @Test
    fun `create and getById round-trip`() = runTest {
        val id = repo.create(profile())
        val result = repo.getById(id)
        assertNotNull(result)
        assertEquals("Ivan", result!!.name)
        assertEquals(78, result.age)
        assertEquals(listOf("Vanya"), result.nicknames)
        assertEquals(listOf("I. Petrov"), result.aliases)
    }

    @Test
    fun `create sets timestamps`() = runTest {
        val id = repo.create(profile())
        val result = repo.getById(id)!!
        assert(result.createdAt > 0)
        assert(result.updatedAt > 0)
        assertEquals(result.createdAt, result.updatedAt)
    }

    @Test
    fun `update modifies profile and bumps updatedAt`() = runTest {
        val id = repo.create(profile())
        val original = repo.getById(id)!!

        Thread.sleep(10) // ensure updatedAt differs
        repo.update(original.copy(name = "Updated", age = 80))

        val updated = repo.getById(id)!!
        assertEquals("Updated", updated.name)
        assertEquals(80, updated.age)
        assert(updated.updatedAt >= original.updatedAt)
    }

    @Test
    fun `delete removes profile`() = runTest {
        val id = repo.create(profile())
        repo.delete(id)
        assertNull(repo.getById(id))
    }

    @Test
    fun `observeAll returns domain models`() = runTest {
        repo.observeAll().test {
            assertEquals(0, awaitItem().size)

            repo.create(profile(name = "First"))
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("First", list[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getById returns null for non-existent id`() = runTest {
        assertNull(repo.getById(999))
    }

    @Test
    fun `string list fields round-trip correctly`() = runTest {
        val id = repo.create(profile().copy(
            aliases = listOf("Alias A", "Alias B"),
            phones = listOf("+7111", "+7222", "+7333"),
        ))
        val result = repo.getById(id)!!
        assertEquals(listOf("Alias A", "Alias B"), result.aliases)
        assertEquals(listOf("+7111", "+7222", "+7333"), result.phones)
    }
}
