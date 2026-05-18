package com.pca.assistant.stt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Honest no-op STT used until [WhisperJniRecognizer] has both
 * `libpca_whisper_jni.so` and a ggml model on disk.
 *
 * Why not Android's built-in [android.speech.SpeechRecognizer]? The system
 * recogniser wants exclusive access to the microphone, which directly
 * conflicts with the always-on [com.pca.assistant.audio.AudioCapture] feed
 * that the foreground service depends on. Trying to use it as a fallback
 * causes "mic busy" failures and unpredictable audio dropouts on Samsung
 * OneUI. The MVP therefore intentionally returns empty transcripts until
 * whisper.cpp is downloaded — the dashboard and Settings surface a clear
 * "download model" CTA so the user knows what to do.
 */
@Singleton
class NoopSpeechRecognizer @Inject constructor() : SpeechRecognizer {
    override val id: String = "noop"
    override suspend fun recognize(pcm: ShortArray, hintLanguage: String?): SttResult =
        SttResult(text = "", confidence = 0f, detectedLanguage = hintLanguage)
}
