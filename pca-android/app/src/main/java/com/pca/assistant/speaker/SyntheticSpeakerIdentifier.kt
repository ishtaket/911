package com.pca.assistant.speaker

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Fallback embedding used only while the ECAPA-TDNN ONNX model has not yet
 * been downloaded. Deterministic per (voice + mic + noise floor), so a single
 * session of enrollment + recognition still works coherently.
 *
 * NEVER promoted to default — [com.pca.assistant.di.BindingsModule] picks
 * [OnnxEcapaIdentifier] whenever the ONNX model is on disk.
 */
@Singleton
class SyntheticSpeakerIdentifier @Inject constructor() : SpeakerIdentifier {

    override val id: String = "synthetic-fallback"
    override val embeddingSize: Int = EMBEDDING_SIZE

    override fun embedding(pcm: ShortArray): FloatArray {
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
            out[i] = ((rms * 0.6 + zcr * 0.4) * 2.0 - 1.0).toFloat()
        }
        return l2Normalize(out)
    }

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
    }
}
