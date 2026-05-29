package com.pca.assistant.stt

import com.pca.assistant.models.ModelRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single [SpeechRecognizer] the rest of the app injects.
 *
 * Resolution order, evaluated per-call so the next chunk picks up changes:
 *   1. [WhisperJniRecognizer]  — production path; requires `libpca_whisper_jni.so`
 *      (built by CMake) and a downloaded ggml model on disk.
 *   2. [NoopSpeechRecognizer]  — honest empty result while we wait for the
 *      user to download a model. Android's built-in STT is NOT used because
 *      it competes for the mic with our always-on capture (see NoopSpeechRecognizer).
 *
 * Both implementations honour the same contract, so consumers (the foreground
 * service, the enrollment flow) never need to know which is active.
 */
@Singleton
class AdaptiveSpeechRecognizer @Inject constructor(
    private val whisper: WhisperJniRecognizer,
    private val noop: NoopSpeechRecognizer,
    private val registry: ModelRegistry,
) : SpeechRecognizer {

    override val id: String
        get() = if (whisperReady()) whisper.id else noop.id

    override suspend fun recognize(pcm: ShortArray, hintLanguage: String?): SttResult =
        if (whisperReady()) whisper.recognize(pcm, hintLanguage)
        else noop.recognize(pcm, hintLanguage)

    override fun release() {
        runCatching { whisper.release() }
    }

    fun whisperReady(): Boolean {
        if (!WhisperJniRecognizer.nativeAvailable) return false
        return registry.isReady(ModelRegistry.WHISPER_TURBO_Q5) ||
            registry.isReady(ModelRegistry.WHISPER_SMALL_Q5)
    }
}
