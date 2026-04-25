package com.rescue911.osint.data.mock

import com.rescue911.osint.domain.model.EvidenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDataTest {

    @Test
    fun `mock cases include hebrew variants`() {
        val variants = MockData.cases.flatMap { it.person.nameVariants }
        assertTrue(variants.any { it.any { ch -> ch.code in 0x0590..0x05FF } })
    }

    @Test
    fun `evidence statuses cover candidate to corroborated`() {
        val statuses = MockData.evidence.map { it.status }.toSet()
        assertTrue(EvidenceStatus.NEEDS_REVIEW in statuses)
        assertTrue(EvidenceStatus.CORROBORATED in statuses)
    }

    @Test
    fun `at least one hypothesis sits inside israel bbox`() {
        val inIsrael = MockData.hypotheses.any {
            (it.lat ?: 0.0) in 31.2..33.5 && (it.lon ?: 0.0) in 34.2..35.9
        }
        assertTrue(inIsrael)
    }

    @Test
    fun `audit log has provider call entries`() {
        assertEquals(true, MockData.auditLog.any { it.action == "provider_call" })
    }
}
