package com.pca.assistant.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams a model download into a `.part` file, swaps it in atomically, and
 * verifies SHA-256 if the spec carries one. Emits coarse progress events so
 * the Settings screen can render a percentage.
 */
@Singleton
class ModelDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val registry: ModelRegistry,
) {

    sealed interface Progress {
        data class Running(val downloaded: Long, val total: Long, val percent: Int) : Progress
        data class Done(val file: File) : Progress
        data class Failed(val reason: String) : Progress
    }

    fun download(spec: ModelSpec, overrideUrl: String? = null): Flow<Progress> = flow {
        val url = (overrideUrl?.trim().takeIf { !it.isNullOrEmpty() }) ?: spec.defaultUrl
        val target = registry.fileFor(spec)
        val part = File(target.parentFile, "${target.name}.part")

        if (target.exists()) {
            emit(Progress.Done(target))
            return@flow
        }
        runCatching { part.delete() }

        val req = Request.Builder().url(url).header("Accept", "application/octet-stream").get().build()
        val resp = try { client.newCall(req).execute() } catch (e: Throwable) {
            emit(Progress.Failed("network: ${e.message}"))
            return@flow
        }
        resp.use { r ->
            if (!r.isSuccessful) {
                emit(Progress.Failed("HTTP ${r.code}"))
                return@flow
            }
            val total = r.body?.contentLength() ?: -1L
            val source = r.body?.byteStream() ?: run {
                emit(Progress.Failed("empty body"))
                return@flow
            }
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            var copied = 0L
            var lastPct = -1
            try {
                part.outputStream().use { sink ->
                    while (true) {
                        val n = source.read(buf)
                        if (n <= 0) break
                        sink.write(buf, 0, n)
                        md.update(buf, 0, n)
                        copied += n
                        if (total > 0) {
                            val pct = ((copied * 100) / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                emit(Progress.Running(copied, total, pct))
                            }
                        } else if (copied / (1024 * 1024) != (copied - n) / (1024 * 1024)) {
                            emit(Progress.Running(copied, -1L, -1))
                        }
                    }
                }
            } catch (e: IOException) {
                runCatching { part.delete() }
                emit(Progress.Failed("io: ${e.message}"))
                return@flow
            }
            if (spec.sha256 != null) {
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(spec.sha256, ignoreCase = true)) {
                    runCatching { part.delete() }
                    emit(Progress.Failed("sha256 mismatch: $actual"))
                    return@flow
                }
            }
            if (!part.renameTo(target)) {
                emit(Progress.Failed("rename failed"))
                return@flow
            }
            emit(Progress.Done(target))
        }
    }.flowOn(Dispatchers.IO)
}
