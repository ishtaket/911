package com.pca.assistant.location

import com.pca.assistant.data.db.entity.PlaceEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure (Android-free) helpers for turning raw GPS into spec-§3.5 "place
 * labels". Extracted from [LocationProvider] so the logic is unit-testable
 * without `FusedLocationProviderClient`.
 */
object PlaceMatcher {

    data class Match(val label: String, val pauseRecording: Boolean)

    fun match(lat: Double, lng: Double, places: List<PlaceEntity>): Match {
        for (p in places) {
            if (distanceMetres(lat, lng, p.lat, p.lng) <= p.radius) {
                return Match(p.label, p.pauseRecording)
            }
        }
        return Match(bucketLabel(lat, lng), false)
    }

    /** Round to ~1 km cell — keeps coordinates out of the LLM payload. */
    fun bucketLabel(lat: Double, lng: Double): String {
        // Locale.ROOT (B-24) — on a RU / IW device the default locale uses
        // a comma as the decimal separator, which would turn the cell into
        // `cell:32,09,34,78` (three commas) and break re-parsing.
        val rl = String.format(java.util.Locale.ROOT, "%.2f", lat)
        val rn = String.format(java.util.Locale.ROOT, "%.2f", lng)
        return "cell:$rl,$rn"
    }

    /** Haversine distance in metres. Both inputs are degrees. */
    fun distanceMetres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
