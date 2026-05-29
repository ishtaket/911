package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest

/**
 * Spec §3.4: `LlmProvider.decide(window): Decision`.
 *
 * The primary provider is `codex CLI`, the fallback is `Gemini CLI`. Neither
 * runs on an Android device directly — the spec calls for a bridge. We
 * therefore ship two implementations:
 *
 *   - [MockLocalProvider]   — deterministic, offline, demonstrates the full
 *                             pipeline end-to-end without network.
 *   - [HttpBridgeProvider]  — POSTs the LlmRequest to a user-controlled HTTP
 *                             bridge (Termux / home server) that fronts codex
 *                             or Gemini.
 *
 * [ProviderRouter] does the primary→fallback health-check switching per §3.4.
 */
interface LlmProvider {
    val id: String
    suspend fun decide(request: LlmRequest): LlmDecision
}

class LlmProviderException(val provider: String, message: String, cause: Throwable? = null) :
    RuntimeException("[$provider] $message", cause)
