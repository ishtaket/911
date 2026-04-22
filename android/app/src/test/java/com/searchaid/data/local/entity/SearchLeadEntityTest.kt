package com.searchaid.data.local.entity

import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.SearchLead
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchLeadEntityTest {

    @Test
    fun `roundtrip preserves all fields`() {
        val lead = SearchLead(
            id = 5,
            caseId = 1,
            type = LeadType.SOCIAL,
            platform = "VK",
            matchedValue = "ivan_petrov",
            textSnippet = "Found matching profile",
            possibleLocationName = "Moscow",
            lat = 55.7558,
            lon = 37.6173,
            timestamp = 5000L,
            confidence = 0.85f,
            status = LeadStatus.CONFIRMED,
        )

        val restored = SearchLeadEntity.fromDomain(lead).toDomain()
        assertEquals(lead, restored)
    }

    @Test
    fun `all lead types roundtrip correctly`() {
        LeadType.entries.forEach { type ->
            val lead = SearchLead(
                caseId = 1, type = type, platform = null, matchedValue = null,
                textSnippet = null, possibleLocationName = null, lat = null,
                lon = null, timestamp = null, confidence = 0f,
            )
            assertEquals(type, SearchLeadEntity.fromDomain(lead).toDomain().type)
        }
    }

    @Test
    fun `all lead statuses roundtrip correctly`() {
        LeadStatus.entries.forEach { status ->
            val lead = SearchLead(
                caseId = 1, type = LeadType.MANUAL, platform = null, matchedValue = null,
                textSnippet = null, possibleLocationName = null, lat = null,
                lon = null, timestamp = null, confidence = 0f, status = status,
            )
            assertEquals(status, SearchLeadEntity.fromDomain(lead).toDomain().status)
        }
    }
}
