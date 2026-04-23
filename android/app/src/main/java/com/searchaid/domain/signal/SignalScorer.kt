package com.searchaid.domain.signal

import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.model.LeadStatus
import com.searchaid.domain.model.LeadType
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.WitnessReport
import javax.inject.Inject

/**
 * Converts raw data points (leads, reports, places, last-seen) into
 * scored Signals. Scoring factors: source reliability, verification
 * status, confidence, and time decay.
 */
class SignalScorer @Inject constructor() {

    fun scoreLastSeen(case_: MissingCase): Signal? {
        val lat = case_.lastSeenLat ?: return null
        val lon = case_.lastSeenLon ?: return null
        return Signal(
            lat = lat, lon = lon,
            score = LAST_SEEN_WEIGHT,
            source = SignalSource.LAST_SEEN,
            label = "Last seen: ${case_.lastSeenLocationName ?: "unknown"}",
            timestampMs = case_.lastSeenTime,
        )
    }

    fun scoreLead(lead: SearchLead, nowMs: Long = System.currentTimeMillis()): Signal? {
        val lat = lead.lat ?: return null
        val lon = lead.lon ?: return null
        if (lead.status == LeadStatus.REJECTED) return null

        val baseWeight = when (lead.status) {
            LeadStatus.CONFIRMED -> LEAD_CONFIRMED_WEIGHT
            LeadStatus.NEW -> LEAD_NEW_WEIGHT
            LeadStatus.ARCHIVED -> LEAD_ARCHIVED_WEIGHT
            LeadStatus.REJECTED -> return null
        }

        val typeFactor = leadTypeFactor(lead.type)
        val confidenceFactor = lead.confidence.coerceIn(0f, 1f)
        val decayFactor = timeDecay(lead.timestamp, nowMs)
        val recencyBoost = recencyBoost(lead.timestamp, nowMs)
        val score = baseWeight * typeFactor * (0.5f + 0.5f * confidenceFactor) * decayFactor * recencyBoost

        return Signal(
            lat = lat, lon = lon,
            score = score,
            source = if (lead.status == LeadStatus.CONFIRMED) SignalSource.LEAD_CONFIRMED else SignalSource.LEAD_NEW,
            label = lead.textSnippet ?: lead.matchedValue ?: "Lead #${lead.id}",
            timestampMs = lead.timestamp,
        )
    }

    fun scoreReport(report: WitnessReport, nowMs: Long = System.currentTimeMillis()): Signal? {
        val lat = report.lat ?: return null
        val lon = report.lon ?: return null
        if (report.status == ReportStatus.REJECTED) return null

        val baseWeight = when (report.status) {
            ReportStatus.VERIFIED -> WITNESS_VERIFIED_WEIGHT
            ReportStatus.NEW -> WITNESS_NEW_WEIGHT
            ReportStatus.REJECTED -> return null
        }

        val confidenceFactor = report.confidence.coerceIn(0f, 1f)
        val decayFactor = timeDecay(report.timestamp, nowMs)
        val recencyBoost = recencyBoost(report.timestamp, nowMs)
        val score = baseWeight * (0.5f + 0.5f * confidenceFactor) * decayFactor * recencyBoost

        return Signal(
            lat = lat, lon = lon,
            score = score,
            source = if (report.status == ReportStatus.VERIFIED) SignalSource.WITNESS_VERIFIED else SignalSource.WITNESS_NEW,
            label = report.possibleLocationName ?: "Witness report #${report.id}",
            timestampMs = report.timestamp,
        )
    }

    fun scoreHistoricalPlace(place: HistoricalPlace): Signal {
        return Signal(
            lat = place.lat, lon = place.lon,
            score = HISTORICAL_WEIGHT,
            source = SignalSource.HISTORICAL_PLACE,
            label = place.title,
        )
    }

    /**
     * Lead type factor: first-person sighting types (WITNESS, MANUAL) are
     * weighted higher than automated/web sources.
     */
    internal fun leadTypeFactor(type: LeadType): Float = when (type) {
        LeadType.WITNESS -> LEAD_TYPE_WITNESS
        LeadType.MANUAL -> LEAD_TYPE_MANUAL
        LeadType.SOCIAL -> LEAD_TYPE_SOCIAL
        LeadType.MESSENGER -> LEAD_TYPE_MESSENGER
        LeadType.WEB -> LEAD_TYPE_WEB
    }

    /**
     * Recency boost: signals less than 1 hour old get a 1.2× multiplier.
     * This prioritizes the freshest data points without penalizing older ones.
     */
    internal fun recencyBoost(timestampMs: Long?, nowMs: Long): Float {
        if (timestampMs == null) return 1.0f
        val hoursAgo = (nowMs - timestampMs).toFloat() / MILLIS_PER_HOUR
        return if (hoursAgo < 1f) RECENCY_BOOST else 1.0f
    }

    /**
     * Time decay: signals lose value over time.
     * Full value in first 6 hours, linear decay to 30% over 72 hours.
     */
    internal fun timeDecay(timestampMs: Long?, nowMs: Long): Float {
        if (timestampMs == null) return 0.7f // unknown time → moderate decay
        val hoursAgo = (nowMs - timestampMs).toFloat() / MILLIS_PER_HOUR
        return when {
            hoursAgo <= 6f -> 1.0f
            hoursAgo >= 72f -> 0.3f
            else -> 1.0f - 0.7f * (hoursAgo - 6f) / 66f
        }
    }

    companion object {
        const val LAST_SEEN_WEIGHT = 1.0f
        const val LEAD_CONFIRMED_WEIGHT = 0.9f
        const val LEAD_NEW_WEIGHT = 0.5f
        const val LEAD_ARCHIVED_WEIGHT = 0.2f
        const val WITNESS_VERIFIED_WEIGHT = 0.85f
        const val WITNESS_NEW_WEIGHT = 0.4f
        const val HISTORICAL_WEIGHT = 0.3f

        // Lead type multipliers: first-person sources score higher
        const val LEAD_TYPE_WITNESS = 1.2f
        const val LEAD_TYPE_MANUAL = 1.1f
        const val LEAD_TYPE_SOCIAL = 1.0f
        const val LEAD_TYPE_MESSENGER = 0.95f
        const val LEAD_TYPE_WEB = 0.85f

        // Very fresh signals (< 1 hour) get a 20% boost
        const val RECENCY_BOOST = 1.2f

        private const val MILLIS_PER_HOUR = 3_600_000f
    }
}
