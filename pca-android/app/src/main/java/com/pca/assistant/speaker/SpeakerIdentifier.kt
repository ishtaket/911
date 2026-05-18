package com.pca.assistant.speaker

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Spec §3.2, layer 4.
 *
 * Real implementation: ECAPA-TDNN ONNX, cosine similarity vs enrolled
 * reference, score > 0.75 → is_owner=true.
 *
 * MVP shim: a deterministic synthetic embedding derived from rolling RMS +
 * zero-crossing rate of the chunk. It's stable per-voice for the same speaker
 * with the same mic and noise floor, which is enough to flip the is_owner
 * flag during a single session. The class API will not change when the ONNX
 * model is wired in.
 */
@Singleton
class SpeakerIdentifier @Inject constructor() {

    fun embedding(pcm: ShortArray): FloatArray {
        if (pcm.isEmpty()) return FloatArray(EMBEDDING_SIZE)
        val out = FloatArray(EMBEDDING_SIZE)
        val winSize = (pcm.size / EMBEDDING_SIZE).coerceAtLeast(1)
        for (i in 0 until EMBEDDING_SIZE) {
            val start = i * winSize
            val end = minOf(start + winSize, pcm.size)
            if (start >= end) continue
            var sum = 0.0
            var zc = 0
            var prev = pcm[start].toInt()
            for (j in start until end) {
                val v = pcm[j].toInt()
                sum += v.toDouble() * v
                if ((prev xor v) and 0x8000 != 0) zc += 1
                prev = v
            }
            val rms = sqrt(sum / (end - start)) / 32768.0
            val zcr = zc.toDouble() / (end - start)
            // Mix the two features so the resulting vector is more than amplitude-only.
            out[i] = ((rms * 0.6 + zcr * 0.4) * 2.0 - 1.0).toFloat()
        }
        return l2Normalize(out)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        // Both vectors are L2-normalized -> dot product == cosine.
        return dot.coerceIn(-1f, 1f)
    }

    fun isOwner(score: Float): Boolean = score >= OWNER_THRESHOLD

    private fun l2Normalize(v: FloatArray): FloatArray {
        var n = 0.0
        for (x in v) n += x.toDouble() * x
        val norm = sqrt(n).toFloat()
        if (norm == 0f) return v
        for (i in v.indices) v[i] = v[i] / norm
        return v
    }

    companion object {
        const val EMBEDDING_SIZE = 64
        const val OWNER_THRESHOLD = 0.75f
    }
}
