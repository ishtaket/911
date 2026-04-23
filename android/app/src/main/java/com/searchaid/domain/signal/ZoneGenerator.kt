package com.searchaid.domain.signal

import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clusters geo-located signals into search zones using distance-based grouping.
 * Each cluster becomes a ScoredZone with aggregated score and contributing signals.
 */
class ZoneGenerator @Inject constructor() {

    /**
     * Groups signals within [clusterRadiusMeters] of each other,
     * computes centroid and radius for each cluster, returns ranked zones.
     */
    fun generate(
        signals: List<Signal>,
        clusterRadiusMeters: Double = DEFAULT_CLUSTER_RADIUS,
    ): List<ScoredZone> {
        if (signals.isEmpty()) return emptyList()

        val remaining = signals.toMutableList()
        val clusters = mutableListOf<MutableList<Signal>>()

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeFirst()
            val cluster = mutableListOf(seed)

            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                // Check distance to centroid of current cluster
                val centroidLat = cluster.map { it.lat }.average()
                val centroidLon = cluster.map { it.lon }.average()
                if (haversineMeters(centroidLat, centroidLon, candidate.lat, candidate.lon) <= clusterRadiusMeters) {
                    cluster.add(candidate)
                    iterator.remove()
                }
            }
            clusters.add(cluster)
        }

        return clusters.map { cluster ->
            val centroidLat = cluster.map { it.lat }.average()
            val centroidLon = cluster.map { it.lon }.average()

            // Radius: max distance from centroid to any signal, with minimum
            val maxDist = cluster.maxOf { haversineMeters(centroidLat, centroidLon, it.lat, it.lon) }
            val radius = maxOf(maxDist + RADIUS_PADDING, MIN_ZONE_RADIUS)

            // Composite score: weighted sum with multi-source correlation bonus, capped at 1.0
            val rawScore = cluster.sumOf { it.score.toDouble() }.toFloat()
            val correlationBonus = sourceCorrelationBonus(cluster)
            val compositeScore = (rawScore * correlationBonus).coerceAtMost(1.0f)

            // Build reason from top signals
            val reason = cluster
                .sortedByDescending { it.score }
                .take(3)
                .joinToString("; ") { "${it.source.name}: ${it.label}" }

            ScoredZone(
                lat = centroidLat,
                lon = centroidLon,
                radiusMeters = radius,
                score = compositeScore,
                reason = reason,
                signals = cluster.toList(),
            )
        }.sortedByDescending { it.score }
    }

    /**
     * Zones with signals from multiple independent source types get a bonus.
     * 2 distinct sources → 10% boost, 3+ → 20% boost.
     * This rewards corroborating evidence from different channels.
     */
    internal fun sourceCorrelationBonus(signals: List<Signal>): Float {
        val distinctSources = signals.map { it.source }.toSet().size
        return when {
            distinctSources >= 3 -> CORRELATION_BONUS_HIGH
            distinctSources == 2 -> CORRELATION_BONUS_MEDIUM
            else -> 1.0f
        }
    }

    companion object {
        const val DEFAULT_CLUSTER_RADIUS = 500.0 // meters
        const val RADIUS_PADDING = 100.0 // meters beyond outermost signal
        const val MIN_ZONE_RADIUS = 200.0 // minimum zone radius
        const val CORRELATION_BONUS_MEDIUM = 1.1f // 2 distinct source types
        const val CORRELATION_BONUS_HIGH = 1.2f   // 3+ distinct source types

        private const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
