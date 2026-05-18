package com.pca.assistant.speaker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.pca.assistant.models.ModelRegistry
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Spec §3.2 / §6.3 — ECAPA-TDNN ONNX runtime via Microsoft ONNX Runtime
 * Android. Input: 16 kHz mono float32 in [-1, 1]. Output: speaker embedding
 * (typically 192 floats; we treat the size as model-defined).
 *
 * Implementation notes:
 *   - The ORT session is lazy: built the first time [embedding] is called
 *     after the model file appears in [ModelRegistry.modelsDir]. This avoids
 *     loading anything during process start.
 *   - Sessions are NOT thread-safe; we serialise calls behind `synchronized`.
 *     On the listening hot path embeddings are O(small) per chunk and the
 *     foreground service runs sequentially anyway, so contention is none.
 *   - If session creation fails (e.g. model file is corrupted), we fall back
 *     to a zero embedding — the speaker score will be 0 and is_owner false
 *     until the user re-downloads the model.
 */
@Singleton
class OnnxEcapaIdentifier @Inject constructor(
    private val registry: ModelRegistry,
) : SpeakerIdentifier {

    override val id: String = "ecapa-tdnn-onnx"

    @Volatile private var session: OrtSession? = null
    @Volatile private var inputName: String? = null
    @Volatile private var resolvedSize: Int = DEFAULT_EMBEDDING_SIZE
    private val lock = Any()

    override val embeddingSize: Int get() = resolvedSize

    override fun embedding(pcm: ShortArray): FloatArray {
        if (pcm.isEmpty()) return FloatArray(resolvedSize)
        val s = ensureSession() ?: return FloatArray(resolvedSize)

        val samples = FloatArray(pcm.size) { i -> pcm[i] / 32768f }
        return try {
            val env = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, samples.size.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), shape).use { tensor ->
                val key = inputName ?: s.inputNames.first()
                s.run(mapOf(key to tensor)).use { results ->
                    val raw = results.get(0)
                    val out = extractFloatArray(raw.value)
                    if (out.isNotEmpty()) {
                        resolvedSize = out.size
                        l2Normalize(out)
                    } else FloatArray(resolvedSize)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "embedding failed: ${t.message}")
            FloatArray(resolvedSize)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractFloatArray(value: Any): FloatArray {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                val first = value.firstOrNull() ?: return FloatArray(0)
                if (first is FloatArray) first
                else if (first is Array<*> && first.firstOrNull() is FloatArray) {
                    @Suppress("UNCHECKED_CAST") (first as Array<FloatArray>)[0]
                } else FloatArray(0)
            }
            else -> FloatArray(0)
        }
    }

    private fun ensureSession(): OrtSession? {
        val current = session
        if (current != null) return current
        synchronized(lock) {
            val existing = session
            if (existing != null) return existing
            val file = registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX)
            if (!file.exists() || file.length() < 1024) return null
            return try {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                val s = env.createSession(file.absolutePath, opts)
                inputName = s.inputNames.first()
                session = s
                Log.i(TAG, "ECAPA session loaded: inputs=${s.inputNames} outputs=${s.outputNames}")
                s
            } catch (t: Throwable) {
                Log.e(TAG, "createSession failed: ${t.message}", t)
                null
            }
        }
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
        private const val TAG = "EcapaOnnx"
        const val DEFAULT_EMBEDDING_SIZE = 192
    }
}
