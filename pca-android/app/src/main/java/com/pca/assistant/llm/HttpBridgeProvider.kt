package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * HTTP bridge provider (spec §3.4, §6.4).
 *
 * The Android device cannot run codex CLI / Gemini CLI natively, so we POST
 * the request envelope to a small user-controlled HTTP service that fronts
 * one or both CLIs. A reference Python bridge is provided at
 * `pca-android/bridge/server.py` in this repo.
 *
 * Wire contract: POST {baseUrl}/decide  body=LlmRequest (JSON) → LlmDecision (JSON).
 */
class HttpBridgeProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: AppSettings,
) : LlmProvider {

    // Hilt does not honour Kotlin constructor default values when generating
    // the factory — declaring `id` in the constructor (even with a default)
    // makes Dagger demand a `String` binding. Keep it as a plain property.
    override val id: String = "http-bridge"

    override suspend fun decide(request: LlmRequest): LlmDecision {
        val base = settings.flow.first().bridgeUrl.trim().trimEnd('/')
        validateBridgeUrl(base)
        val body = json.encodeToString(LlmRequest.serializer(), request)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val http = Request.Builder()
            .url("$base/decide")
            .header("Accept", "application/json")
            .post(body)
            .build()
        return try {
            client.newCall(http).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw LlmProviderException(id, "HTTP ${resp.code}")
                }
                val raw = resp.body?.string()
                    ?: throw LlmProviderException(id, "Empty body")
                json.decodeFromString(LlmDecision.serializer(), raw)
            }
        } catch (e: LlmProviderException) {
            throw e
        } catch (e: Throwable) {
            throw LlmProviderException(id, e.message ?: e.javaClass.simpleName, e)
        }
    }

    /**
     * SECURITY (PCA-S-3): Reject schemes other than http/https so we don't
     * forward our serialised window payload through `file://`,
     * `javascript:`, `content://`, `ftp://` or other handlers that OkHttp
     * would otherwise refuse with a confusing IAE. Cleartext http is
     * still allowed at this layer; the platform's network_security_config
     * narrows it further to RFC-1918 + loopback ranges.
     */
    internal fun validateBridgeUrl(base: String) {
        if (base.isBlank()) {
            throw LlmProviderException(id, "Bridge URL not configured in settings")
        }
        val lower = base.lowercase()
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            throw LlmProviderException(id, "Bridge URL scheme must be http or https")
        }
        // Authority required (host part, optional port). e.g. http:// alone is invalid.
        val afterScheme = base.substringAfter("://").substringBefore('/')
        if (afterScheme.isBlank()) {
            throw LlmProviderException(id, "Bridge URL is missing a host")
        }
    }
}
