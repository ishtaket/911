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

    // ---- Source Correlation Bonus ----

    @Test
    fun `sourceCorrelationBonus single source returns 1`() {
        val signals = listOf(
            signal(source = SignalSource.LEAD_NEW),
            signal(source = SignalSource.LEAD_NEW),
        )
        assertEquals(1.0f, generator.sourceCorrelationBonus(signals))
    }

    @Test
    fun `sourceCorrelationBonus two sources returns medium bonus`() {
        val signals = listOf(
            signal(source = SignalSource.LEAD_NEW),
            signal(source = SignalSource.WITNESS_VERIFIED),
        )
        assertEquals(ZoneGenerator.CORRELATION_BONUS_MEDIUM, generator.sourceCorrelationBonus(signals))
    }

    @Test
    fun `sourceCorrelationBonus three sources returns high bonus`() {
        val signals = listOf(
            signal(source = SignalSource.LEAD_NEW),
            signal(source = SignalSource.WITNESS_VERIFIED),
            signal(source = SignalSource.HISTORICAL_PLACE),
        )
        assertEquals(ZoneGenerator.CORRELATION_BONUS_HIGH, generator.sourceCorrelationBonus(signals))
    }

    @Test
    fun `multi-source zone scores higher than single-source zone`() {
        val multiSource = listOf(
            signal(lat = 55.75, lon = 37.62, score = 0.3f, source = SignalSource.LEAD_NEW),
            signal(lat = 55.7501, lon = 37.6201, score = 0.3f, source = SignalSource.WITNESS_NEW),
        )
        val singleSource = listOf(
            signal(lat = 55.80, lon = 37.70, score = 0.3f, source = SignalSource.LEAD_NEW),
            signal(lat = 55.8001, lon = 37.7001, score = 0.3f, source = SignalSource.LEAD_NEW),
        )
        val zones = generator.generate(multiSource + singleSource, clusterRadiusMeters = 500.0)
        assertEquals(2, zones.size)
        // Multi-source zone should rank first due to correlation bonus
        assertTrue(zones[0].signals.map { it.source }.toSet().size > 1)
    }

    @Test
    fun `correlation bonus still capped at 1`() {
        val signals = listOf(
            signal(lat = 55.75, lon = 37.62, score = 0.5f, source = SignalSource.LEAD_CONFIRMED),
            signal(lat = 55.7501, lon = 37.6201, score = 0.5f, source = SignalSource.WITNESS_VERIFIED),
            signal(lat = 55.7502, lon = 37.6202, score = 0.5f, source = SignalSource.HISTORICAL_PLACE),
        )
        val zones = generator.generate(signals, clusterRadiusMeters = 500.0)
        assertEquals(1.0f, zones[0].score) // 1.5 * 1.2 = 1.8 → capped at 1.0
    }
}
