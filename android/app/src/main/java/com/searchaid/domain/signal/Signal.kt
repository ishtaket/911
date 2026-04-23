package com.searchaid.domain.signal

/**
 * A normalized, geo-located data point contributing to search zone scoring.
 * Created from leads, witness reports, or historical places.
 */
data class Signal(
    val lat: Double,
    val lon: Double,
    val score: Float,
    val source: SignalSource,
    val label: String,
    val timestampMs: Long? = null,
)

enum class SignalSource {
    LEAD_CONFIRMED,
    LEAD_NEW,
    WITNESS_VERIFIED,
    WITNESS_NEW,
    HISTORICAL_PLACE,
    LAST_SEEN,
}

/**
 * A search zone with its composite score and the signals that contributed to it.
 */
data class ScoredZone(
    val lat: Double,
    val lon: Double,
    val radiusMeters: Double,
    val score: Float,
    val reason: String,
    val signals: List<Signal>,
)
