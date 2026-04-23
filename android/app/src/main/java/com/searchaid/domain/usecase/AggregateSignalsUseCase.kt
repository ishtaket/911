package com.searchaid.domain.usecase

import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.model.SearchLead
import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.signal.ScoredZone
import com.searchaid.domain.signal.SignalScorer
import com.searchaid.domain.signal.ZoneGenerator
import javax.inject.Inject

/**
 * Orchestrates signal aggregation: collects all geo-located data for a case,
 * scores each signal, clusters into zones, returns ranked ScoredZones.
 */
class AggregateSignalsUseCase @Inject constructor(
    private val scorer: SignalScorer,
    private val generator: ZoneGenerator,
) {
    operator fun invoke(
        case_: MissingCase,
        leads: List<SearchLead>,
        reports: List<WitnessReport>,
        historicalPlaces: List<HistoricalPlace>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ScoredZone> {
        val signals = buildList {
            // Last seen location (highest priority)
            scorer.scoreLastSeen(case_)?.let { add(it) }

            // All non-rejected leads with coordinates
            leads.forEach { lead ->
                scorer.scoreLead(lead, nowMs)?.let { add(it) }
            }

            // All non-rejected witness reports with coordinates
            reports.forEach { report ->
                scorer.scoreReport(report, nowMs)?.let { add(it) }
            }

            // Historical places
            historicalPlaces.forEach { place ->
                add(scorer.scoreHistoricalPlace(place))
            }
        }

        return generator.generate(signals)
    }
}
