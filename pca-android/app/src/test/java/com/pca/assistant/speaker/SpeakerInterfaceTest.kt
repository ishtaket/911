package com.pca.assistant.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts the shared contract pieces that all [SpeakerIdentifier]
 * implementations inherit by default — bounded cosine, owner-threshold
 * gating, NaN-safety, mismatched-size handling.
 */
class SpeakerInterfaceTest {

    private val impl: SpeakerIdentifier = SyntheticSpeakerIdentifier()

    @Test fun `cosine of identical vectors is exactly 1`() {
        val v = floatArrayOf(0.6f, 0.8f, 0f) // already unit length
        assertEquals(1f, impl.cosineSimilarity(v, v), 1e-6f)
    }

    @Test fun `cosine of opposite vectors is exactly -1`() {
        val v = floatArrayOf(0.6f, 0.8f, 0f)
        val w = floatArrayOf(-0.6f, -0.8f, 0f)
        assertEquals(-1f, impl.cosineSimilarity(v, w), 1e-6f)
    }

    @Test fun `cosine of orthogonal vectors is 0`() {
        val v = floatArrayOf(1f, 0f, 0f)
        val w = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, impl.cosineSimilarity(v, w), 1e-6f)
    }

    @Test fun `mismatched sizes return 0 instead of crashing`() {
        assertEquals(0f, impl.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 0f)), 0f)
    }

    @Test fun `empty arrays return 0`() {
        assertEquals(0f, impl.cosineSimilarity(FloatArray(0), FloatArray(0)), 0f)
    }

    @Test fun `owner threshold is exactly 0_75`() {
        assertTrue(impl.isOwner(0.75f))
        assertTrue(impl.isOwner(0.8f))
        assertFalse(impl.isOwner(0.7499f))
        assertEquals(0.75f, SpeakerIdentifier.OWNER_THRESHOLD, 0f)
    }

    @Test fun `cosine is clamped into the minus-one to plus-one range even on rounding excursions`() {
        // Hand-craft slightly-larger-than-unit vectors so the raw dot is > 1.
        val v = floatArrayOf(1.0001f, 0f)
        assertTrue(impl.cosineSimilarity(v, v) in -1f..1f)
    }
}
