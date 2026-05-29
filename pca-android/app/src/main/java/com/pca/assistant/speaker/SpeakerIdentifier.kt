package com.pca.assistant.speaker

/**
 * Spec §3.2 / §6.3 — speaker identification layer.
 *
 * Default production implementation is [OnnxEcapaIdentifier], which runs an
 * ECAPA-TDNN ONNX model via ONNX Runtime Android. If the model file is not
 * yet on disk (user hasn't tapped "download" in Settings), the binding falls
 * back to [SyntheticSpeakerIdentifier] so the enrollment + service pipeline
 * stays functional. Both implementations honour the same threshold contract
 * (cosine ≥ 0.75 → owner).
 */
interface SpeakerIdentifier {
    /** Implementation tag for diagnostics. */
    val id: String

    /** Produce an L2-normalised embedding for a 16 kHz mono PCM clip. */
    fun embedding(pcm: ShortArray): FloatArray

    /** Cosine similarity between two L2-normalised embeddings of equal size. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    fun isOwner(score: Float): Boolean = score >= OWNER_THRESHOLD

    /** Embedding size used by this implementation (varies by model). */
    val embeddingSize: Int

    companion object {
        const val OWNER_THRESHOLD: Float = 0.75f
    }
}
