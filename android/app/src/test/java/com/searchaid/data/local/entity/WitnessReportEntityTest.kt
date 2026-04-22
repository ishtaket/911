package com.searchaid.data.local.entity

import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.WitnessReport
import org.junit.Assert.assertEquals
import org.junit.Test

class WitnessReportEntityTest {

    @Test
    fun `roundtrip preserves all fields`() {
        val report = WitnessReport(
            id = 3,
            caseId = 1,
            sourceName = "Maria",
            sourceType = "phone_call",
            text = "Saw an elderly man near the bus stop",
            timestamp = 3000L,
            possibleLocationName = "Bus stop on Lenina St",
            lat = 55.76,
            lon = 37.62,
            confidence = 0.7f,
            status = ReportStatus.VERIFIED,
        )

        val restored = WitnessReportEntity.fromDomain(report).toDomain()
        assertEquals(report, restored)
    }

    @Test
    fun `all report statuses roundtrip correctly`() {
        ReportStatus.entries.forEach { status ->
            val report = WitnessReport(
                caseId = 1, text = "test", status = status,
                sourceName = null, sourceType = null,
                possibleLocationName = null, lat = null, lon = null,
            )
            assertEquals(status, WitnessReportEntity.fromDomain(report).toDomain().status)
        }
    }
}
