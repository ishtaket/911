package com.pca.assistant.stt

/**
 * Spec §3.1, layer 3 — local STT.
 *
 * The full spec aims for whisper.cpp via JNI with on-demand model download
 * (small Q5 / large-v3 turbo Q5 / Ivrit.AI booster). For the MVP we ship a
 * working Android-native pipeline: the always-on foreground service collects
 * VAD-gated PCM and writes it as a transcript via this single-method facade.
 *
 * Implementations:
 *   - [AndroidSpeechRecognizerImpl] — uses Android's built-in SpeechRecognizer
 *     (works on the S21 Ultra out of the box, no model download required).
 *     Selected when the user picks "Android SpeechRecognizer" in settings.
 *   - WhisperJniRecognizer (TODO) — to be wired in once the libwhisper.so
 *     artifact is built and the user has downloaded a ggml model.
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
