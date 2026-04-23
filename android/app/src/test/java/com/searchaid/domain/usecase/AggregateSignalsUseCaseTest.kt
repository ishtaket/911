package com.searchaid.domain.usecase

import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.signal.SignalScorer
import com.searchaid.domain.signal.ZoneGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregateSignalsUseCaseTest {

    private val useCase = AggregateSignalsUseCase(SignalScorer(), ZoneGenerator())
    private val now = 1_000_000_000L

    private val case_ = MissingCase(
        id = 1, personId = 1, status = CaseStatus.ACTIVE,
        lastSeenTime = now - 3_600_000, lastSeenLocationName = "Central Park",
        lastSeenLat = 55.75, lastSeenLon = 37.62,
        clothesDescription = null, notes = null, operatorId = null,
    )

    private fun lead(
        lat: Double, lon: Double,
        status: LeadStatus = LeadStatus.CONFIRMED,
        confidence: Float = 0.8f,
    ) = SearchLead(
        id = 0, caseId = 1, type = LeadType.MANUAL,
        platform = null, matchedValue = null, textSnippet = "Lead",
        possibleLocationName = null, lat = lat, lon = lon,
        timestamp = now - 7_200_000, confidence = confidence, status = status,
    )

    private fun report(lat: Double, lon: Double) = WitnessReport(
        id = 0, caseId = 1, sourceName = "Witness", sourceType = "phone",
        text = "Saw someone", timestamp = now - 3_600_000,
        possibleLocationName = "Bus stop", lat = lat, lon = lon,
        confidence = 0.7f, status = ReportStatus.VERIFIED,
    )

    private fun place(lat: Double, lon: Double) = HistoricalPlace(
        id = 0, personId = 1, title = "Home", lat = lat, lon = lon,
        source = null, note = null,
    )

    @Test
    fun `produces zone from last seen location alone`() {
        val zones = useCase(case_, emptyList(), emptyList(), emptyList(), now)
        assertEquals(1, zones.size)
        assertTrue(zones[0].score > 0)
    }

    @Test
    fun `clusters nearby signals into single zone`() {
        val zones = useCase(
            case_,
            leads = listOf(lead(55.7502, 37.6202)),
            reports = listOf(report(55.7501, 37.6201)),
            historicalPlaces = listOf(place(55.7503, 37.6203)),
            nowMs = now,
        )
        // All within 500m of each other → single zone
        assertEquals(1, zones.size)
        assertTrue(zones[0].signals.size >= 3) // last seen + lead + report + place
    }

    @Test
    fun `separates distant signal groups`() {
        val zones = useCase(
            case_,
            leads = listOf(lead(55.80, 37.70)), // ~6km away
            reports = emptyList(),
            historicalPlaces = emptyList(),
            nowMs = now,
        )
        assertEquals(2, zones.size) // last seen zone + lead zone
    }

    @Test
    fun `rejected leads are excluded`() {
        val zones = useCase(
            case_.copy(lastSeenLat = null), // no last seen
            leads = listOf(
                lead(55.75, 37.62, status = LeadStatus.REJECTED),
            ),
            reports = emptyList(),
            historicalPlaces = emptyList(),
            nowMs = now,
        )
        assertEquals(0, zones.size)
    }

    @Test
    fun `empty inputs with no last-seen produces no zones`() {
        val noLocationCase = case_.copy(lastSeenLat = null, lastSeenLon = null)
        val zones = useCase(noLocationCase, emptyList(), emptyList(), emptyList(), now)
        assertEquals(0, zones.size)
    }

    @Test
    fun `zones are ranked by score`() {
        // Confirmed lead near (55.80) should score higher than historical place at (55.85)
        val zones = useCase(
            case_.copy(lastSeenLat = null, lastSeenLon = null),
            leads = listOf(lead(55.80, 37.62, confidence = 1.0f)),
            reports = emptyList(),
            historicalPlaces = listOf(place(55.85, 37.62)),
            nowMs = now,
        )
        assertEquals(2, zones.size)
        assertTrue(zones[0].score >= zones[1].score)
    }

    @Test
    fun `historical places contribute to zones`() {
        val zones = useCase(
            case_.copy(lastSeenLat = null, lastSeenLon = null),
            leads = emptyList(),
            reports = emptyList(),
            historicalPlaces = listOf(place(55.75, 37.62)),
            nowMs = now,
        )
        assertEquals(1, zones.size)
        assertEquals("Home", zones[0].signals[0].label)
    }
}
