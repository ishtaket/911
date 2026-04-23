package com.searchaid.domain.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneGeneratorTest {

    private val generator = ZoneGenerator()

    private fun signal(
        lat: Double = 55.75,
        lon: Double = 37.62,
        score: Float = 0.5f,
        source: SignalSource = SignalSource.LEAD_NEW,
        label: String = "test",
    ) = Signal(lat, lon, score, source, label)

    @Test
    fun `empty signals produces no zones`() {
        assertEquals(emptyList<ScoredZone>(), generator.generate(emptyList()))
    }

    @Test
    fun `single signal produces one zone`() {
        val zones = generator.generate(listOf(signal()))
        assertEquals(1, zones.size)
        assertEquals(55.75, zones[0].lat, 0.001)
        assertEquals(37.62, zones[0].lon, 0.001)
    }

    @Test
    fun `single zone has minimum radius`() {
        val zones = generator.generate(listOf(signal()))
        assertTrue(zones[0].radiusMeters >= ZoneGenerator.MIN_ZONE_RADIUS)
    }

    @Test
    fun `nearby signals cluster into one zone`() {
        // Two points ~100m apart in Moscow
        val signals = listOf(
            signal(lat = 55.7500, lon = 37.6200, score = 0.5f),
            signal(lat = 55.7505, lon = 37.6205, score = 0.3f),
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertEquals(1, zones.size)
        assertEquals(2, zones[0].signals.size)
    }

    @Test
    fun `distant signals produce separate zones`() {
        // Two points ~5km apart
        val signals = listOf(
            signal(lat = 55.75, lon = 37.62, score = 0.5f),
            signal(lat = 55.80, lon = 37.62, score = 0.3f),
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertEquals(2, zones.size)
    }

    @Test
    fun `zones are sorted by score descending`() {
        val signals = listOf(
            signal(lat = 55.75, lon = 37.62, score = 0.3f),
            signal(lat = 55.80, lon = 37.70, score = 0.9f),
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertTrue(zones[0].score >= zones[1].score)
    }

    @Test
    fun `cluster score is sum of signal scores capped at 1`() {
        val signals = listOf(
            signal(lat = 55.75, lon = 37.62, score = 0.6f),
            signal(lat = 55.7501, lon = 37.6201, score = 0.6f),
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertEquals(1, zones.size)
        assertEquals(1.0f, zones[0].score) // capped at 1.0
    }

    @Test
    fun `zone reason includes top signal labels`() {
        val signals = listOf(
            signal(lat = 55.75, lon = 37.62, score = 0.8f, label = "Main signal"),
            signal(lat = 55.7501, lon = 37.6201, score = 0.2f, label = "Secondary"),
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertTrue(zones[0].reason.contains("Main signal"))
    }

    @Test
    fun `haversineMeters calculates correct distance`() {
        // Moscow coordinates ~555m apart
        val dist = ZoneGenerator.haversineMeters(55.7500, 37.6200, 55.7550, 37.6200)
        assertTrue("Expected ~555m, got $dist", dist in 500.0..600.0)
    }

    @Test
    fun `haversineMeters same point is zero`() {
        assertEquals(0.0, ZoneGenerator.haversineMeters(55.75, 37.62, 55.75, 37.62), 0.01)
    }

    @Test
    fun `three clusters from three distant groups`() {
        val signals = listOf(
            signal(lat = 55.75, lon = 37.62),     // Group A
            signal(lat = 55.7501, lon = 37.6201), // Group A
            signal(lat = 55.80, lon = 37.62),     // Group B
            signal(lat = 55.85, lon = 37.62),     // Group C
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertEquals(3, zones.size)
    }
}
