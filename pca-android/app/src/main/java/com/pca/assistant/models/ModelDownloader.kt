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

    private fun schemeOf(url: String): String =
        url.substringBefore("://", missingDelimiterValue = "(none)")

    internal fun isAcceptableModelUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("https://")) return true
        if (!lower.startsWith("http://")) return false
        // Extract host part for the cleartext allowlist.
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            (host.startsWith("172.") && run {
                // 172.16.0.0/12 — second octet 16..31
                val second = host.substringAfter("172.").substringBefore('.').toIntOrNull() ?: return@run false
                second in 16..31
            })
    }

    fun download(spec: ModelSpec, overrideUrl: String? = null): Flow<Progress> = flow {
        val url = (overrideUrl?.trim().takeIf { !it.isNullOrEmpty() }) ?: spec.defaultUrl
        // SECURITY (PCA-S-5): model weights are effectively executable code
        // (whisper.cpp + ONNX run model-defined ops), so a MITM-tampered
        // .bin / .onnx is roughly equivalent to arbitrary code substitution.
        // Require https for anything that crosses the public internet;
        // allow http only for loopback and RFC-1918 private ranges, matching
        // the cleartext exception in network_security_config.xml.
        if (!isAcceptableModelUrl(url)) {
            emit(Progress.Failed("refused: model URL must be https or LAN-cleartext (got: ${schemeOf(url)})"))
            return@flow
        }
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
