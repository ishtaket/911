package com.searchaid.domain.signal

/**
 * A weighted geographic point for heat map rendering.
 * Kept Android-free so it can be unit-tested without Robolectric.
 */
data class HeatMapPoint(
    val lat: Double,
    val lon: Double,
    val intensity: Double,
)

/**
 * Converts scored signals into heat map data points.
 *
 * Each signal becomes one or more heat map points — high-score signals
 * are amplified so the heat map visually highlights probable areas.
 * Signals are also interpolated between clusters to produce a smoother gradient.
 */
class HeatMapDataBuilder @javax.inject.Inject constructor() {

    /**
     * Build heat map points from raw signals.
     * Each signal produces a primary point whose intensity = score (0..1).
     * High-confidence signals (>= 0.6) also produce secondary points
     * at small offsets to widen their heat footprint.
     */
    fun build(signals: List<Signal>): List<HeatMapPoint> {
        if (signals.isEmpty()) return emptyList()

        val points = mutableListOf<HeatMapPoint>()

        for (signal in signals) {
            val intensity = signal.score.toDouble().coerceIn(0.01, 1.0)

            // Primary point
            points += HeatMapPoint(signal.lat, signal.lon, intensity)

            // Secondary spread for high-confidence signals
            if (signal.score >= HIGH_CONFIDENCE_THRESHOLD) {
                val offset = SPREAD_OFFSET_DEGREES
                points += HeatMapPoint(signal.lat + offset, signal.lon, intensity * 0.6)
                points += HeatMapPoint(signal.lat - offset, signal.lon, intensity * 0.6)
                points += HeatMapPoint(signal.lat, signal.lon + offset, intensity * 0.6)
                points += HeatMapPoint(signal.lat, signal.lon - offset, intensity * 0.6)
            }
        }

        return points
    }

    companion object {
        /** Signals at or above this score get secondary spread points. */
        const val HIGH_CONFIDENCE_THRESHOLD = 0.6f

        /** ~50 meters in degrees latitude (approximate). */
        const val SPREAD_OFFSET_DEGREES = 0.00045
    }
}
