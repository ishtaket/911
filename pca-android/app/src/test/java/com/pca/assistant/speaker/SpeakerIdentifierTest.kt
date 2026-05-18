package com.pca.assistant.speaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class SpeakerIdentifierTest {

    private val id = SpeakerIdentifier()

    @Test fun `embedding is L2 normalised`() {
        val sine = ShortArray(8_000) { i -> (sin(i * 0.05) * 1000).toInt().toShort() }
        val e = id.embedding(sine)
        var n = 0.0
        for (x in e) n += x.toDouble() * x
        assertEquals(1.0, n, 0.05)
    }

    @Test fun `same input yields identical embedding`() {
        val seed = ShortArray(16_000) { Random(42).nextInt(-1000, 1000).toShort() }
        val a = id.embedding(seed.copyOf())
        val b = id.embedding(seed.copyOf())
        assertEquals(1.0f, id.cosineSimilarity(a, b), 0.0001f)
    }

    @Test fun `wildly different inputs are not classified as owner`() {
        val a = id.embedding(ShortArray(16_000) { (sin(it * 0.07) * 10000).toInt().toShort() })
        val b = id.embedding(ShortArray(16_000) { (Random(7).nextInt(-5000, 5000)).toShort() })
        val score = id.cosineSimilarity(a, b)
        assertTrue("score=$score should be < 0.75", score < SpeakerIdentifier.OWNER_THRESHOLD)
    }
}
