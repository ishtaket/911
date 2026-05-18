package com.pca.assistant.models

import android.content.Context
import com.pca.assistant.settings.SttModelChoice
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for everything that has to be downloaded at runtime
 * (per spec §12.7 — the APK MUST stay slim, models come on demand).
 *
 * Every entry carries a stable filename, a default URL pointing at the
 * canonical public mirror, and (optionally) an expected SHA-256 the downloader
 * verifies before swapping in the file. Users / packagers can override the URL
 * via settings — the file path stays the same so the rest of the app does not
 * need to know.
 */
data class ModelSpec(
    val id: String,
    val filename: String,
    val defaultUrl: String,
    val approxMb: Int,
    /** Lowercase hex, optional. When null the downloader skips integrity verification. */
    val sha256: String? = null,
)

@Singleton
class ModelRegistry @Inject constructor(
    private val context: Context,
) {

    fun modelsDir(): File =
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun fileFor(spec: ModelSpec): File = File(modelsDir(), spec.filename)

    fun isReady(spec: ModelSpec): Boolean {
        val f = fileFor(spec)
        return f.exists() && f.length() > 1024
    }

    fun whisperFor(choice: SttModelChoice): ModelSpec = when (choice) {
        SttModelChoice.WHISPER_SMALL_Q5 -> WHISPER_SMALL_Q5
        SttModelChoice.WHISPER_TURBO_Q5 -> WHISPER_TURBO_Q5
        SttModelChoice.WHISPER_TURBO_PLUS_IVRIT,
        SttModelChoice.ANDROID_BUILT_IN -> WHISPER_TURBO_Q5
    }

    fun ivritFor(choice: SttModelChoice): ModelSpec? = when (choice) {
        SttModelChoice.WHISPER_TURBO_PLUS_IVRIT -> IVRIT_TURBO_Q5
        else -> null
    }

    companion object {

        /**
         * ggerganov/whisper.cpp official ggml releases on Hugging Face — the
         * URL scheme is documented in the whisper.cpp README and is the same
         * one the upstream `models/download-ggml-model.sh` script uses.
         */
        val WHISPER_SMALL_Q5 = ModelSpec(
            id = "whisper-small-q5_0",
            filename = "ggml-small-q5_0.bin",
            defaultUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_0.bin",
            approxMb = 466,
        )

        /** Spec §12.4 recommended baseline — large-v3 turbo Q5 (~800 MB). */
        val WHISPER_TURBO_Q5 = ModelSpec(
            id = "whisper-large-v3-turbo-q5_0",
            filename = "ggml-large-v3-turbo-q5_0.bin",
            defaultUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            approxMb = 800,
        )

        /**
         * Ivrit.AI fine-tune. The upstream `ivrit-ai/whisper-large-v3-turbo-ct2`
         * is in CTranslate2 format; for whisper.cpp consumption you need the
         * GGML conversion. Override this URL in settings to point at your
         * conversion until an official GGML upload exists.
         */
        val IVRIT_TURBO_Q5 = ModelSpec(
            id = "ivrit-turbo-q5_0",
            filename = "ggml-ivrit-turbo-q5_0.bin",
            defaultUrl = "https://huggingface.co/ivrit-ai/whisper-large-v3-turbo-ggml/resolve/main/ggml-model-q5_0.bin",
            approxMb = 1024,
        )

        /**
         * Speaker embedding ONNX model.
         *
         * IMPORTANT (B-19): the default URL is a PLACEHOLDER pointing at a
         * sherpa-onnx ERes2Net export. That model expects pre-extracted
         * fbank/mel features as input, NOT raw 16 kHz PCM, which is what
         * [com.pca.assistant.speaker.OnnxEcapaIdentifier] currently feeds
         * it. The download will succeed but the embeddings will be noise,
         * so cosine-vs-enrolled stays near zero and owner identification
         * silently fails to fire.
         *
         * To get real owner identification today, ship a speaker-embedding
         * ONNX that accepts raw 16 kHz mono float audio on its input
         * (shape `[1, n_samples]`). Speechbrain's `spkrec-ecapa-voxceleb`
         * exported with `--input-type=audio` is the canonical choice;
         * Pyannote's `pyannote/embedding` is another. Drop the converted
         * `.onnx` into `filesDir/models/ecapa-tdnn.onnx` (or override the
         * URL via the same downloader entry point).
         *
         * Until a working model is in place, [AdaptiveSpeakerIdentifier]
         * routes to [com.pca.assistant.speaker.SyntheticSpeakerIdentifier]
         * — owner identification works correctly for the device's own
         * mic + noise floor profile.
         */
        val ECAPA_TDNN_ONNX = ModelSpec(
            id = "ecapa-tdnn-onnx",
            filename = "ecapa-tdnn.onnx",
            defaultUrl = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/main/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
            approxMb = 27,
        )
    }
}
