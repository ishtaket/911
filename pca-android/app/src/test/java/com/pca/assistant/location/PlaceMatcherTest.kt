package com.pca.assistant.location

import com.pca.assistant.data.db.entity.PlaceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMatcherTest {

    private val home = PlaceEntity(
        id = 1, label = "home", lat = 32.0853, lng = 34.7818,
        radius = 100f, semanticTag = "home", pauseRecording = true,
    )
    private val work = PlaceEntity(
        id = 2, label = "work", lat = 32.0900, lng = 34.7900,
        radius = 200f, semanticTag = "work", pauseRecording = false,
    )

    @Test fun `point inside the home geofence returns home + pause`() {
        val m = PlaceMatcher.match(32.0853, 34.7818, listOf(home, work))
        assertEquals("home", m.label)
        assertTrue(m.pauseRecording)
    }

    @Test fun `point well outside all geofences returns a bucket cell label`() {
        val m = PlaceMatcher.match(40.7128, -74.0060, listOf(home, work))
        assertTrue("label was ${m.label}", m.label.startsWith("cell:"))
        assertFalse(m.pauseRecording)
    }

    @Test fun `bucket label rounds to 2 decimals`() {
        assertEquals("cell:40.71,-74.01", PlaceMatcher.bucketLabel(40.71284, -74.00567))
    }

    @Test fun `first match wins when geofences overlap`() {
        val a = home.copy(id = 1, label = "first", lat = 0.0, lng = 0.0, radius = 1_000_000f)
        val b = home.copy(id = 2, label = "second", lat = 0.0, lng = 0.0, radius = 1_000_000f)
        val m = PlaceMatcher.match(0.0, 0.0, listOf(a, b))
        assertEquals("first", m.label)
    }

    @Test fun `haversine returns roughly correct distances`() {
        // Tel Aviv ↔ Jerusalem is ~54 km on the ground.
        val d = PlaceMatcher.distanceMetres(32.0853, 34.7818, 31.7683, 35.2137)
        assertTrue("got d=$d", d in 50_000.0..60_000.0)
    }

    @Test fun `empty places list always yields a bucket label`() {
        val m = PlaceMatcher.match(31.0, 35.0, emptyList())
        assertTrue(m.label.startsWith("cell:"))
        assertFalse(m.pauseRecording)
    }

    @Test fun `bucket label is locale-stable on RU and DE (B-24)`() {
        // Both locales use comma-as-decimal-separator by default. Without an
        // explicit Locale.ROOT, String.format("%.2f", 32.0853) would render
        // as "32,09" and turn the cell label into "cell:32,09,34,78" — three
        // commas, unparseable. Pin the invariant.
        val originalDefault = java.util.Locale.getDefault()
        try {
            for (loc in listOf(java.util.Locale("ru", "RU"), java.util.Locale("de", "DE"), java.util.Locale("fr", "FR"))) {
                java.util.Locale.setDefault(loc)
                val label = PlaceMatcher.bucketLabel(32.0853, 34.7818)
                assertEquals("cell:32.09,34.78", label)
            }
        } finally {
            java.util.Locale.setDefault(originalDefault)
        }
    }
}
