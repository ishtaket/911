package com.searchaid.domain.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatMapDataBuilderTest {

    private val builder = HeatMapDataBuilder()

    private fun signal(
        lat: Double = 55.75,
        lon: Double = 37.61,
        score: Float = 0.5f,
        source: SignalSource = SignalSource.LEAD_NEW,
        label: String = "test",
    ) = Signal(lat, lon, score, source, label)

    @Test
    fun `empty signals returns empty list`() {
        val result = builder.build(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single low-confidence signal produces one point`() {
        val result = builder.build(listOf(signal(score = 0.3f)))
        assertEquals(1, result.size)
        assertEquals(55.75, result[0].lat, 0.0001)
        assertEquals(37.61, result[0].lon, 0.0001)
        assertEquals(0.3, result[0].intensity, 0.01)
    }

    @Test
    fun `high-confidence signal produces primary plus 4 spread points`() {
        val result = builder.build(listOf(signal(score = 0.8f)))
        // 1 primary + 4 secondary = 5
        assertEquals(5, result.size)
    }

    @Test
    fun `signal at exact threshold 0_6 gets spread points`() {
        val result = builder.build(listOf(signal(score = 0.6f)))
        assertEquals(5, result.size)
    }

    @Test
    fun `signal just below threshold 0_59 gets no spread`() {
        val result = builder.build(listOf(signal(score = 0.59f)))
        assertEquals(1, result.size)
    }

    @Test
    fun `spread points have 60 percent of primary intensity`() {
        val score = 0.8f
        val result = builder.build(listOf(signal(score = score)))
        val primary = result[0]
        val secondary = result.drop(1)

        assertEquals(score.toDouble(), primary.intensity, 0.01)
        secondary.forEach { point ->
            assertEquals(score * 0.6, point.intensity, 0.01)
        }
    }

    @Test
    fun `spread points are offset from primary`() {
        val result = builder.build(listOf(signal(lat = 55.75, lon = 37.61, score = 0.8f)))
        val primary = result[0]
        val secondary = result.drop(1)

        // All secondary should be at SPREAD_OFFSET_DEGREES distance
        secondary.forEach { point ->
            val latDiff = Math.abs(point.lat - primary.lat)
            val lonDiff = Math.abs(point.lon - primary.lon)
            val totalDiff = latDiff + lonDiff
            assertEquals(HeatMapDataBuilder.SPREAD_OFFSET_DEGREES, totalDiff, 0.00001)
        }
    }

    @Test
    fun `multiple signals produce correct total points`() {
        val signals = listOf(
            signal(lat = 55.75, lon = 37.61, score = 0.3f), // 1 point (below threshold)
            signal(lat = 55.76, lon = 37.62, score = 0.9f), // 5 points (above threshold)
            signal(lat = 55.77, lon = 37.63, score = 0.5f), // 1 point (below threshold)
        )
        val result = builder.build(signals)
        assertEquals(7, result.size)
    }

    @Test
    fun `very low score is clamped to 0_01`() {
        val result = builder.build(listOf(signal(score = 0.0f)))
        assertEquals(0.01, result[0].intensity, 0.001)
    }

    @Test
    fun `score above 1 is clamped to 1_0`() {
        // ScoredZone score is capped at 1.0, but test defensive clamping
        val result = builder.build(listOf(signal(score = 1.5f)))
        assertEquals(1.0, result[0].intensity, 0.001)
    }

    @Test
    fun `last seen signal with full score produces spread`() {
        val lastSeen = signal(score = 1.0f, source = SignalSource.LAST_SEEN, label = "Last Seen")
        val result = builder.build(listOf(lastSeen))
        assertEquals(5, result.size)
        assertEquals(1.0, result[0].intensity, 0.001)
    }

    @Test
    fun `historical place signals with low score produce single points`() {
        val places = listOf(
            signal(lat = 55.70, lon = 37.50, score = 0.3f, source = SignalSource.HISTORICAL_PLACE),
            signal(lat = 55.71, lon = 37.51, score = 0.3f, source = SignalSource.HISTORICAL_PLACE),
        )
        val result = builder.build(places)
        assertEquals(2, result.size)
    }
}
