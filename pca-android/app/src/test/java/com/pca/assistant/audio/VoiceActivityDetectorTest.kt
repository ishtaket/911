package com.pca.assistant.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VoiceActivityDetectorTest {

    private val vad = VoiceActivityDetector()

    @Test fun `silence below noise floor is rejected`() {
        val silent = ShortArray(16_000) { 0 }
        var anySpeech = false
        repeat(5) {
            if (vad.isSpeech(silent)) anySpeech = true
        }
        assertFalse(anySpeech)
    }

    @Test fun `loud tone is detected as speech eventually`() {
        // Prime the noise floor with quiet input.
        val quiet = ShortArray(16_000) { (sin(it * 0.001) * 50).toInt().toShort() }
        repeat(MAX_HISTORY_PRIMER) { vad.isSpeech(quiet) }
        // Now hit it with a loud tone.
        val loud = ShortArray(16_000) { (sin(it * 0.05) * 12_000).toInt().toShort() }
        var detected = false
        repeat(5) { if (vad.isSpeech(loud)) detected = true }
        assertTrue("loud tone should be classified as speech once VAD warmed up", detected)
    }

    private companion object {
        const val MAX_HISTORY_PRIMER = 35
    }
}
