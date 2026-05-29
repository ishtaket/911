package com.pca.assistant.speaker

import com.pca.assistant.models.ModelRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single [SpeakerIdentifier] that the rest of the app injects. It transparently
 * delegates each call to [OnnxEcapaIdentifier] once the ECAPA-TDNN ONNX model
 * is on disk, and to [SyntheticSpeakerIdentifier] before that.
 *
 * This avoids forcing the user to restart the app after they tap "download
 * speaker model" in Settings — the next chunk just starts using ONNX.
 *
 * Embedding size: when the active impl changes (synthetic → onnx) the saved
 * enrolled embedding will be at the synthetic size and cosine will return 0.
 * The owner is prompted to re-enroll once the ONNX model is in place; the
 * Re-enroll button in Settings handles this.
 */
@Singleton
class AdaptiveSpeakerIdentifier @Inject constructor(
    private val onnx: OnnxEcapaIdentifier,
    private val synthetic: SyntheticSpeakerIdentifier,
    private val registry: ModelRegistry,
) : SpeakerIdentifier {

    override val id: String
        get() = if (onnxReady()) onnx.id else synthetic.id

    override val embeddingSize: Int
        get() = if (onnxReady()) onnx.embeddingSize else synthetic.embeddingSize

    override fun embedding(pcm: ShortArray): FloatArray =
        if (onnxReady()) onnx.embedding(pcm) else synthetic.embedding(pcm)

    private fun onnxReady(): Boolean = registry.isReady(ModelRegistry.ECAPA_TDNN_ONNX)
}
