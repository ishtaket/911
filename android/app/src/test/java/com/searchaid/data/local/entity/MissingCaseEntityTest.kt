package com.searchaid.data.local.entity

import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.MissingCase
import org.junit.Assert.assertEquals
import org.junit.Test

class MissingCaseEntityTest {

    @Test
    fun `roundtrip preserves all fields`() {
        val case = MissingCase(
            id = 1,
            personId = 42,
            status = CaseStatus.ACTIVE,
            createdAt = 1000L,
            lastSeenTime = 900L,
            lastSeenLocationName = "Park entrance",
            lastSeenLat = 55.7558,
            lastSeenLon = 37.6173,
            clothesDescription = "Blue jacket",
            notes = "Left without phone",
            operatorId = "op1",
        )

        val restored = MissingCaseEntity.fromDomain(case).toDomain()
        assertEquals(case, restored)
    }

    @Test
    fun `all statuses roundtrip correctly`() {
        CaseStatus.entries.forEach { status ->
            val case = MissingCase(
                personId = 1,
                status = status,
                lastSeenTime = null,
                lastSeenLocationName = null,
                lastSeenLat = null,
                lastSeenLon = null,
                clothesDescription = null,
                notes = null,
                operatorId = null,
            )
            val restored = MissingCaseEntity.fromDomain(case).toDomain()
            assertEquals(status, restored.status)
        }
    }

    @Test
    fun `roundtrip with all nulls`() {
        val case = MissingCase(
            personId = 1,
            lastSeenTime = null,
            lastSeenLocationName = null,
            lastSeenLat = null,
            lastSeenLon = null,
            clothesDescription = null,
            notes = null,
            operatorId = null,
        )

        val restored = MissingCaseEntity.fromDomain(case).toDomain()
        assertEquals(case, restored)
    }
}
