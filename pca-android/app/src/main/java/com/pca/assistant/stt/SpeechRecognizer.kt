package com.pca.assistant.stt

/**
 * Spec §3.1, layer 3 — local STT.
 *
 * Production wiring routes through [com.pca.assistant.stt.AdaptiveSpeechRecognizer],
 * which prefers:
 *   - [WhisperJniRecognizer] — whisper.cpp v1.7.1 via JNI, NEON-tuned for
 *     Exynos 2100. Model is downloaded on demand into the app's filesDir.
 *   - [NoopSpeechRecognizer]   — honest empty result for the window between
 *     first install and model download. Android's system SpeechRecognizer
 *     is intentionally NOT used as a fallback: it requires exclusive
 *     access to the mic and would steal it from the always-on
 *     [com.pca.assistant.audio.AudioCapture] feed.
 *
 * Both implementations satisfy this single-method contract so the foreground
 * service stays agnostic.
 */
interface SpeechRecognizer {
    val id: String

    /**
     * Hand a PCM 16-kHz mono buffer to the recognizer. Returns the recognised
     * text (possibly empty if nothing was decoded with sufficient confidence).
     */
    suspend fun recognize(pcm: ShortArray, hintLanguage: String?): SttResult

    fun release() {}
}

data class SttResult(
    val text: String,
    val confidence: Float,
    val detectedLanguage: String?,
)
