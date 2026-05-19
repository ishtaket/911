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
        // Per-chunk 4-feature embedding so wildly different waveforms land on
        // distinct directions in the 64-D space. A single blended feature
        // collapses any stationary signal (constant or sine) to a uniform
        // vector — and after L2 normalize, every uniform vector points the
        // same way, so cos(sine, noise) ≈ 1.0 and the owner check would
        // misclassify any input as the owner.
        //
        // Per chunk we emit: RMS (loudness), ZCR (frequency-ish content),
        // peak-to-peak range (separates constants from oscillations), and
        // signed mean (DC offset — a constant signal has a non-zero mean,
        // a zero-mean oscillation has ~0). Together these cleanly separate
        // sine, white noise, and constant signals from each other.
        val chunks = EMBEDDING_SIZE / 4
        val winSize = (pcm.size / chunks).coerceAtLeast(1)
        for (c in 0 until chunks) {
            val start = c * winSize
            val end = minOf(start + winSize, pcm.size)
            if (start >= end) continue
            var sumSq = 0.0
            var sumSigned = 0.0
            var zc = 0
            var mn = Int.MAX_VALUE
            var mx = Int.MIN_VALUE
            var prev = pcm[start].toInt()
            for (j in start until end) {
                val v = pcm[j].toInt()
                sumSq += v.toDouble() * v
                sumSigned += v
                if ((prev xor v) and 0x8000 != 0) zc += 1
                if (v < mn) mn = v
                if (v > mx) mx = v
                prev = v
            }
            val n = (end - start).toDouble()
            val rms = sqrt(sumSq / n) / 32768.0
            val zcr = zc.toDouble() / n
            val range = (mx - mn).toDouble() / 65536.0
            val mean = (sumSigned / n) / 32768.0
            out[c * 4]     = rms.toFloat()
            out[c * 4 + 1] = zcr.toFloat()
            out[c * 4 + 2] = range.toFloat()
            out[c * 4 + 3] = mean.toFloat()
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
