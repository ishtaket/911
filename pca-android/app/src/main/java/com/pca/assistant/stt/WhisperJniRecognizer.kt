package com.pca.assistant.stt

import android.util.Log
import com.pca.assistant.models.ModelRegistry
import com.pca.assistant.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §3.1 / §12 — primary STT for the production pipeline.
 *
 * Backed by whisper.cpp v1.7.1 compiled for arm64-v8a via the CMake script in
 * `app/src/main/cpp/`. The ggml model file is downloaded on demand by
 * [com.pca.assistant.models.ModelDownloader] into the app's filesDir — the APK
 * stays slim.
 *
 * Until the user has downloaded a model this recogniser is in [Ready.NoModel]
 * state and silently returns empty results; [ListeningService] tolerates
 * empties and the dashboard / settings UI surfaces a "download model" CTA.
 */
@Singleton
class WhisperJniRecognizer @Inject constructor(
    private val registry: ModelRegistry,
    private val settings: AppSettings,
) : SpeechRecognizer {

    override val id: String = "whisper.cpp"

    // Java monitor (not coroutine Mutex) so [release] is a non-suspend
    // function and can be called from non-coroutine contexts like
    // [com.pca.assistant.service.ListeningService.onDestroy] without a
    // runBlocking-on-Main hazard. recognize() takes the same lock during
    // inference, so release() is correctly serialised with in-flight calls.
    private val lock = Any()

    /** Null until the first successful nativeInit. */
    @Volatile private var ctxPtr: Long = 0L
    @Volatile private var loadedFor: String? = null
    /** Track size + mtime so a wipe-and-redownload reloads the model (B-2). */
    @Volatile private var loadedSize: Long = 0L
    @Volatile private var loadedMtime: Long = 0L

    enum class Ready { Loaded, NoModel, NativeMissing }

    fun readyState(): Ready {
        if (!nativeAvailable) return Ready.NativeMissing
        // Resolved synchronously below; the model file existence check is cheap.
        return if (anyModelOnDisk()) Ready.Loaded else Ready.NoModel
    }

    override suspend fun recognize(pcm: ShortArray, hintLanguage: String?): SttResult {
        if (!nativeAvailable) return SttResult("", 0f, hintLanguage)
        if (pcm.isEmpty()) return SttResult("", 0f, hintLanguage)

        val cfg = settings.flow.first()
        val spec = registry.whisperFor(cfg.sttModel)
        val modelFile = registry.fileFor(spec)
        if (!modelFile.exists() || modelFile.length() < 1024) {
            // File disappeared since last load (Settings → Delete / wipe).
            // Drop the cached ctx so we don't keep the deleted model resident
            // in memory until process death.
            if (ctxPtr != 0L) release()
            return SttResult("", 0f, hintLanguage)
        }
        ensureLoaded(modelFile)
        if (ctxPtr == 0L) return SttResult("", 0f, hintLanguage)

        val text = withContext(Dispatchers.Default) {
            // Whisper.cpp is not internally thread-safe across a single
            // context; the synchronized lock also pairs with release() to
            // prevent use-after-free (B-26).
            synchronized(lock) {
                val ptr = ctxPtr
                if (ptr == 0L) "" else nativeRecognize(ptr, pcm, hintLanguage, recommendedThreads())
            }
        }
        return SttResult(
            text = text.trim(),
            confidence = if (text.isBlank()) 0f else 0.85f,
            detectedLanguage = hintLanguage,
        )
    }

    /**
     * Release the native whisper_context. B-26: takes the same Java
     * monitor that [recognize] holds for the duration of inference, so we
     * never call nativeRelease() while another coroutine is inside
     * nativeRecognize() with the same ctx pointer (use-after-free in C++).
     * Brief blocking on contention — wipe is rare and short.
     */
    override fun release() {
        synchronized(lock) {
            val ptr = ctxPtr
            ctxPtr = 0L
            loadedFor = null
            loadedSize = 0L
            loadedMtime = 0L
            if (nativeAvailable && ptr != 0L) {
                runCatching { nativeRelease(ptr) }
            }
        }
    }

    private fun ensureLoaded(modelFile: File) {
        // Fast path — same file, same size, same mtime → reuse loaded ctx.
        if (ctxPtr != 0L &&
            loadedFor == modelFile.absolutePath &&
            loadedSize == modelFile.length() &&
            loadedMtime == modelFile.lastModified()
        ) return

        synchronized(lock) {
            if (ctxPtr != 0L &&
                loadedFor == modelFile.absolutePath &&
                loadedSize == modelFile.length() &&
                loadedMtime == modelFile.lastModified()
            ) return@synchronized
            // Drop stale ctx — wipe / redownload / model swap (B-2).
            if (ctxPtr != 0L) {
                runCatching { nativeRelease(ctxPtr) }
                ctxPtr = 0L
                loadedFor = null
                loadedSize = 0L
                loadedMtime = 0L
            }
            val ptr = runCatching {
                nativeInit(modelFile.absolutePath, /* useGpu = */ false, recommendedThreads())
            }.getOrElse {
                Log.e(TAG, "nativeInit threw", it); 0L
            }
            if (ptr != 0L) {
                ctxPtr = ptr
                loadedFor = modelFile.absolutePath
                loadedSize = modelFile.length()
                loadedMtime = modelFile.lastModified()
                Log.i(TAG, "whisper loaded: ${modelFile.name} (${nativeSystemInfo()})")
            } else {
                Log.w(TAG, "whisper failed to load ${modelFile.name}")
            }
        }
    }

    private fun anyModelOnDisk(): Boolean {
        val dir = registry.modelsDir()
        return dir.listFiles { f -> f.name.startsWith("ggml-") && f.extension == "bin" }?.isNotEmpty() == true
    }

    private fun recommendedThreads(): Int {
        // Exynos 2100: 1 X1 + 3 A78 (big) + 4 A55. Use big cores only — A55
        // throttles under sustained STT and hurts wall time more than it helps.
        return Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    }

    // ------------------------------------------------------------------ JNI

    private external fun nativeInit(modelPath: String, useGpu: Boolean, nThreads: Int): Long
    private external fun nativeRecognize(ctxPtr: Long, pcm: ShortArray, language: String?, nThreads: Int): String
    private external fun nativeRelease(ctxPtr: Long)
    private external fun nativeSystemInfo(): String

    companion object {
        private const val TAG = "WhisperJni"

        /** False if `libpca_whisper_jni.so` couldn't be loaded (e.g. unsupported ABI in dev). */
        @JvmStatic
        val nativeAvailable: Boolean = runCatching {
            System.loadLibrary("pca_whisper_jni")
            true
        }.getOrElse {
            Log.w(TAG, "libpca_whisper_jni.so not loaded: ${it.message}")
            false
        }
    }
}
