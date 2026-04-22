package com.searchaid.data.repository

import app.cash.turbine.test
import com.searchaid.data.local.RoomTestBase
import com.searchaid.domain.model.SearchZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchZoneRepositoryIntegrationTest : RoomTestBase() {

    private lateinit var repo: SearchZoneRepositoryImpl

    @Before
    fun setUp() {
        repo = SearchZoneRepositoryImpl(db.searchZoneDao())
    }

    private fun zone(
        caseId: Long = 1L,
        score: Float = 0.5f,
    ) = SearchZone(
        caseId = caseId, lat = 55.75, lon = 37.61,
        radiusMeters = 500.0, score = score, reason = "Test zone",
    )

    @Test
    fun `add and observeByCase round-trip`() = runTest {
        repo.add(zone(caseId = 1L, score = 0.8f))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(0.8f, list[0].score, 0.001f)
            assertFalse(list[0].checked)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `markChecked updates zone`() = runTest {
        val id = repo.add(zone())

        repo.markChecked(id)

        repo.observeByCase(1L).test {
            assertTrue(awaitItem()[0].checked)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase returns zones ordered by score desc`() = runTest {
        repo.add(zone(caseId = 1L, score = 0.3f))
        repo.add(zone(caseId = 1L, score = 0.9f))
        repo.add(zone(caseId = 1L, score = 0.6f))

        repo.observeByCase(1L).test {
            val list = awaitItem()
            assertEquals(0.9f, list[0].score, 0.001f)
            assertEquals(0.6f, list[1].score, 0.001f)
            assertEquals(0.3f, list[2].score, 0.001f)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `observeByCase filters by caseId`() = runTest {
        repo.add(zone(caseId = 1L))
        repo.add(zone(caseId = 2L))

        repo.observeByCase(1L).test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }
}
