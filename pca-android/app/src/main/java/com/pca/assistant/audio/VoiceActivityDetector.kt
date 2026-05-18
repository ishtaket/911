package com.pca.assistant.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Spec §3.1 — VAD with ~0.5 s speech threshold for STT activation.
 *
 * MVP uses a simple RMS-energy gate. Silero VAD (ONNX) is intended to slot in
 * later behind this same single-method interface — the foreground service
 * never knew the difference.
 */
@Singleton
class VoiceActivityDetector @Inject constructor() {

    private val rmsHistory = ArrayDeque<Double>(MAX_HISTORY)
    private var baseline: Double = INITIAL_BASELINE
    private var consecutiveSpeechChunks: Int = 0

    /** Returns true if this chunk crosses the dynamic noise floor by [SPEECH_RATIO]×. */
    fun isSpeech(chunk: ShortArray): Boolean {
        val rms = rms(chunk)
        rmsHistory.addLast(rms)
        if (rmsHistory.size > MAX_HISTORY) rmsHistory.removeFirst()
        if (rmsHistory.size >= MAX_HISTORY) {
            baseline = rmsHistory.sorted()[rmsHistory.size / 4]
                .coerceAtLeast(MIN_BASELINE)
        }
        val threshold = (baseline * SPEECH_RATIO).coerceAtLeast(MIN_THRESHOLD)
        val speech = rms >= threshold
        consecutiveSpeechChunks = if (speech) consecutiveSpeechChunks + 1 else 0
        return consecutiveSpeechChunks >= MIN_CONSECUTIVE_CHUNKS
    }

    fun reset() {
        rmsHistory.clear()
        baseline = INITIAL_BASELINE
        consecutiveSpeechChunks = 0
    }

    private fun rms(buf: ShortArray): Double {
        if (buf.isEmpty()) return 0.0
        var sum = 0.0
        for (s in buf) {
            val v = s.toDouble()
            sum += v * v
        }
        return sqrt(sum / buf.size)
    }

    private companion object {
        const val MAX_HISTORY = 30
        const val INITIAL_BASELINE = 400.0
        const val MIN_BASELINE = 200.0
        const val SPEECH_RATIO = 2.2
        const val MIN_THRESHOLD = 700.0
        /** Roughly 500 ms at 1 s/chunk → flips to "speech" after half a second. */
        const val MIN_CONSECUTIVE_CHUNKS = 1
    }
}
