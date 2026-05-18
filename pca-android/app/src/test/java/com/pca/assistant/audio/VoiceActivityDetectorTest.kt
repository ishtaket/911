package com.pca.assistant.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class VoiceActivityDetectorTest {

    private lateinit var vad: VoiceActivityDetector

    @Before fun setUp() { vad = VoiceActivityDetector() }

    @Test fun `silence below noise floor is rejected`() {
        val silent = ShortArray(16_000) { 0 }
        var anySpeech = false
        repeat(5) { if (vad.isSpeech(silent)) anySpeech = true }
        assertFalse(anySpeech)
    }

    @Test fun `loud tone is detected as speech once the floor warms up`() {
        val quiet = ShortArray(16_000) { (sin(it * 0.001) * 50).toInt().toShort() }
        repeat(PRIMER) { vad.isSpeech(quiet) }
        val loud = ShortArray(16_000) { (sin(it * 0.05) * 12_000).toInt().toShort() }
        var detected = false
        repeat(5) { if (vad.isSpeech(loud)) detected = true }
        assertTrue("loud tone should be classified as speech once VAD warmed up", detected)
    }

    @Test fun `reset wipes history`() {
        val loud = ShortArray(16_000) { (sin(it * 0.05) * 12_000).toInt().toShort() }
        repeat(PRIMER) { vad.isSpeech(loud) }
        vad.reset()
        val silent = ShortArray(16_000) { 0 }
        repeat(5) { assertFalse(vad.isSpeech(silent)) }
    }

    @Test fun `empty buffer is treated as silence`() {
        assertFalse(vad.isSpeech(ShortArray(0)))
    }

    @Test fun `single quiet chunk does not flip baseline`() {
        // History needs MAX_HISTORY (30) chunks before baseline updates.
        // One quiet chunk in isolation should never produce speech.
        val quiet = ShortArray(16_000) { (sin(it * 0.001) * 50).toInt().toShort() }
        assertFalse(vad.isSpeech(quiet))
    }

    @Test fun `clipping square wave is also detected as speech`() {
        val quiet = ShortArray(16_000) { 10 }
        repeat(PRIMER) { vad.isSpeech(quiet) }
        val square = ShortArray(16_000) { if (it and 0x80 == 0) -16_000 else 16_000 }
        var detected = false
        repeat(3) { if (vad.isSpeech(square)) detected = true }
        assertTrue(detected)
    }

    private companion object {
        const val PRIMER = 35
    }
}
