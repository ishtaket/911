package com.searchaid.domain.signal

import com.searchaid.domain.model.CaseStatus
import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.WitnessReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalScorerTest {

    private val scorer = SignalScorer()
    private val now = 1_000_000_000L

    private fun case_(lat: Double? = 55.75, lon: Double? = 37.62) = MissingCase(
        id = 1, personId = 1, status = CaseStatus.ACTIVE,
        lastSeenTime = now - 3_600_000, lastSeenLocationName = "Park",
        lastSeenLat = lat, lastSeenLon = lon,
        clothesDescription = null, notes = null, operatorId = null,
    )

    private fun lead(
        status: LeadStatus = LeadStatus.NEW,
        confidence: Float = 0.8f,
        lat: Double? = 55.76,
        lon: Double? = 37.63,
        timestamp: Long? = now - 3_600_000,
    ) = SearchLead(
        id = 1, caseId = 1, type = LeadType.MANUAL,
        platform = null, matchedValue = "test", textSnippet = "Test lead",
        possibleLocationName = "Near park", lat = lat, lon = lon,
        timestamp = timestamp, confidence = confidence, status = status,
    )

    private fun report(
        status: ReportStatus = ReportStatus.NEW,
        confidence: Float = 0.7f,
        lat: Double? = 55.77,
        lon: Double? = 37.64,
    ) = WitnessReport(
        id = 1, caseId = 1, sourceName = "Witness",
        sourceType = "phone", text = "Saw someone matching",
        timestamp = now - 7_200_000, possibleLocationName = "Bus stop",
        lat = lat, lon = lon, confidence = confidence, status = status,
    )

    // ---- Last Seen ----

    @Test
    fun `scoreLastSeen returns signal with full weight`() {
        val signal = scorer.scoreLastSeen(case_())
        assertNotNull(signal)
        assertEquals(SignalScorer.LAST_SEEN_WEIGHT, signal!!.score)
        assertEquals(SignalSource.LAST_SEEN, signal.source)
    }

    @Test
    fun `scoreLastSeen returns null when no coordinates`() {
        assertNull(scorer.scoreLastSeen(case_(lat = null)))
        assertNull(scorer.scoreLastSeen(case_(lon = null)))
    }

    // ---- Leads ----

    @Test
    fun `scoreLead confirmed has higher weight than new`() {
        val confirmed = scorer.scoreLead(lead(status = LeadStatus.CONFIRMED), now)
        val new = scorer.scoreLead(lead(status = LeadStatus.NEW), now)
        assertNotNull(confirmed)
        assertNotNull(new)
        assertTrue(confirmed!!.score > new!!.score)
    }

    @Test
    fun `scoreLead rejected returns null`() {
        assertNull(scorer.scoreLead(lead(status = LeadStatus.REJECTED), now))
    }

    @Test
    fun `scoreLead without coordinates returns null`() {
        assertNull(scorer.scoreLead(lead(lat = null), now))
        assertNull(scorer.scoreLead(lead(lon = null), now))
    }

    @Test
    fun `scoreLead higher confidence increases score`() {
        val high = scorer.scoreLead(lead(confidence = 1.0f), now)
        val low = scorer.scoreLead(lead(confidence = 0.2f), now)
        assertTrue(high!!.score > low!!.score)
    }

    @Test
    fun `scoreLead archived has low weight`() {
        val archived = scorer.scoreLead(lead(status = LeadStatus.ARCHIVED), now)
        assertNotNull(archived)
        assertTrue(archived!!.score < 0.3f)
    }

    // ---- Witness Reports ----

    @Test
    fun `scoreReport verified has higher weight than new`() {
        val verified = scorer.scoreReport(report(status = ReportStatus.VERIFIED), now)
        val new = scorer.scoreReport(report(status = ReportStatus.NEW), now)
        assertTrue(verified!!.score > new!!.score)
    }

    @Test
    fun `scoreReport rejected returns null`() {
        assertNull(scorer.scoreReport(report(status = ReportStatus.REJECTED), now))
    }

    @Test
    fun `scoreReport without coordinates returns null`() {
        assertNull(scorer.scoreReport(report(lat = null), now))
    }

    // ---- Historical Places ----

    @Test
    fun `scoreHistoricalPlace returns signal with fixed weight`() {
        val place = HistoricalPlace(1, 1, "Home", 55.75, 37.62, null, null)
        val signal = scorer.scoreHistoricalPlace(place)
        assertEquals(SignalScorer.HISTORICAL_WEIGHT, signal.score)
        assertEquals(SignalSource.HISTORICAL_PLACE, signal.source)
        assertEquals("Home", signal.label)
    }

    // ---- Time Decay ----

    @Test
    fun `timeDecay full value within 6 hours`() {
        val decay = scorer.timeDecay(now - 3_600_000, now) // 1 hour ago
        assertEquals(1.0f, decay)
    }

    @Test
    fun `timeDecay minimum at 72 hours`() {
        val decay = scorer.timeDecay(now - 72 * 3_600_000L, now)
        assertEquals(0.3f, decay, 0.01f)
    }

    @Test
    fun `timeDecay gradual between 6 and 72 hours`() {
        val at24h = scorer.timeDecay(now - 24 * 3_600_000L, now)
        assertTrue(at24h > 0.3f)
        assertTrue(at24h < 1.0f)
    }

    @Test
    fun `timeDecay null timestamp returns moderate value`() {
        assertEquals(0.7f, scorer.timeDecay(null, now))
    }

    // ---- Lead Type Factor ----

    @Test
    fun `leadTypeFactor witness highest of lead types`() {
        val witness = scorer.leadTypeFactor(LeadType.WITNESS)
        val web = scorer.leadTypeFactor(LeadType.WEB)
        assertTrue(witness > web)
        assertEquals(SignalScorer.LEAD_TYPE_WITNESS, witness)
    }

    @Test
    fun `leadTypeFactor manual higher than social`() {
        val manual = scorer.leadTypeFactor(LeadType.MANUAL)
        val social = scorer.leadTypeFactor(LeadType.SOCIAL)
        assertTrue(manual > social)
    }

    @Test
    fun `leadTypeFactor web lowest`() {
        val web = scorer.leadTypeFactor(LeadType.WEB)
        LeadType.entries.forEach { type ->
            assertTrue("$type should be >= WEB", scorer.leadTypeFactor(type) >= web)
        }
    }

    @Test
    fun `scoreLead witness type scores higher than web type same status`() {
        val witnessLead = scorer.scoreLead(
            lead(status = LeadStatus.NEW).copy(type = LeadType.WITNESS), now,
        )
        val webLead = scorer.scoreLead(
            lead(status = LeadStatus.NEW).copy(type = LeadType.WEB), now,
        )
        assertTrue(witnessLead!!.score > webLead!!.score)
    }

    // ---- Recency Boost ----

    @Test
    fun `recencyBoost within 1 hour returns boost`() {
        val boost = scorer.recencyBoost(now - 30 * 60_000L, now) // 30 min ago
        assertEquals(SignalScorer.RECENCY_BOOST, boost)
    }

    @Test
    fun `recencyBoost after 1 hour returns 1`() {
        val boost = scorer.recencyBoost(now - 2 * 3_600_000L, now) // 2 hours ago
        assertEquals(1.0f, boost)
    }

    @Test
    fun `recencyBoost exactly 1 hour returns 1`() {
        val boost = scorer.recencyBoost(now - 3_600_000L, now) // exactly 1 hour
        assertEquals(1.0f, boost)
    }

    @Test
    fun `recencyBoost null timestamp returns 1`() {
        assertEquals(1.0f, scorer.recencyBoost(null, now))
    }

    @Test
    fun `scoreLead very recent lead gets recency boost`() {
        val recent = scorer.scoreLead(lead(timestamp = now - 30 * 60_000L), now)
        val older = scorer.scoreLead(lead(timestamp = now - 3_600_000L), now)
        // Same base weight, confidence, and time decay (both within 6h)
        // but recent one gets 1.2× recency boost
        assertTrue(recent!!.score > older!!.score)
    }
}
