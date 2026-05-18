package com.pca.assistant.llm

import com.pca.assistant.data.db.dao.LlmHealthDao
import com.pca.assistant.data.db.entity.LlmHealthEntity
import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.ProviderMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §3.4 — provider management.
 *
 * Routing rules:
 *   - If user picked [ProviderMode.MOCK], always [mock].
 *   - If user picked [ProviderMode.BRIDGE]:
 *       primary  = bridge (codex CLI front)
 *       fallback = mock   (so the pipeline never blocks notifications)
 *   - After [SWITCH_AFTER_FAILURES] consecutive failures on the primary,
 *     the next window will go straight to the fallback, and primary is
 *     retried again after [REPROBE_AFTER_MS] ms.
 *   - Every call is logged into `llm_health`.
 */
@Singleton
class ProviderRouter @Inject constructor(
    private val mock: MockLocalProvider,
    private val bridge: HttpBridgeProvider,
    private val healthDao: LlmHealthDao,
    private val settings: AppSettings,
) {

    @Volatile private var consecutiveBridgeFailures: Int = 0
    @Volatile private var lastBridgeFailureAt: Long = 0L

    suspend fun decide(request: LlmRequest): Pair<String, LlmDecision> {
        val mode = settings.flow.first().providerMode
        return when (mode) {
            ProviderMode.MOCK -> callTracked(mock, request)
            ProviderMode.BRIDGE -> callWithFallback(request)
        }
    }

    private suspend fun callWithFallback(request: LlmRequest): Pair<String, LlmDecision> {
        val now = System.currentTimeMillis()
        val cooldown = consecutiveBridgeFailures >= SWITCH_AFTER_FAILURES &&
            (now - lastBridgeFailureAt) < REPROBE_AFTER_MS
        if (cooldown) {
            return callTracked(mock, request)
        }
        return try {
            val r = callTracked(bridge, request)
            consecutiveBridgeFailures = 0
            r
        } catch (e: LlmProviderException) {
            consecutiveBridgeFailures += 1
            lastBridgeFailureAt = System.currentTimeMillis()
            callTracked(mock, request)
        }
    }

    private suspend fun callTracked(p: LlmProvider, request: LlmRequest): Pair<String, LlmDecision> {
        val start = System.currentTimeMillis()
        try {
            val decision = p.decide(request)
            healthDao.insert(
                LlmHealthEntity(
                    ts = System.currentTimeMillis(),
                    provider = p.id,
                    success = true,
                    errorCode = null,
                    latencyMs = System.currentTimeMillis() - start,
                )
            )
            return p.id to decision
        } catch (e: Throwable) {
            healthDao.insert(
                LlmHealthEntity(
                    ts = System.currentTimeMillis(),
                    provider = p.id,
                    success = false,
                    errorCode = e.javaClass.simpleName,
                    latencyMs = System.currentTimeMillis() - start,
                )
            )
            if (e is LlmProviderException) throw e
            throw LlmProviderException(p.id, e.message ?: "unknown", e)
        }
    }

    private companion object {
        const val SWITCH_AFTER_FAILURES = 3
        const val REPROBE_AFTER_MS = 5 * 60_000L
    }
}
