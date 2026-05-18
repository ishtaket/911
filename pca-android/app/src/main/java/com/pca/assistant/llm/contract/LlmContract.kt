package com.pca.assistant.llm.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for both directions of the LLM evaluator (spec §3.3, §5).
 *
 * The decision producer is one of:
 *   - [com.pca.assistant.llm.MockLocalProvider]   — fully offline; deterministic.
 *   - [com.pca.assistant.llm.HttpBridgeProvider]  — POSTs to a user-hosted bridge
 *     that fronts codex CLI (primary) or Gemini CLI (fallback).
 */

@Serializable
data class LlmRequest(
    @SerialName("system_prompt") val systemPrompt: String,
    @SerialName("l3_profile") val l3Profile: String,
    @SerialName("l2_day") val l2Day: String,
    @SerialName("l1_hour") val l1Hour: String,
    @SerialName("previous_decision") val previousDecision: LlmDecision? = null,
    @SerialName("open_threads") val openThreads: List<OpenThreadDto> = emptyList(),
    @SerialName("window") val window: WindowPayload,
    @SerialName("instruction") val instruction: String,
    /** ISO-639-1 of expected reply, derived from L3 profile + window language. */
    @SerialName("reply_language") val replyLanguage: String,
)

@Serializable
data class WindowPayload(
    @SerialName("window_id") val windowId: Long,
    @SerialName("start_ts") val startTs: Long,
    @SerialName("end_ts") val endTs: Long,
    val transcript: String,
    @SerialName("location_label") val locationLabel: String?,
    @SerialName("is_owner_present") val isOwnerPresent: Boolean,
    val tags: List<String> = emptyList(),
)

@Serializable
data class OpenThreadDto(
    val id: String,
    val topic: String,
    val context: String,
    @SerialName("opened_at") val openedAt: Long,
    val due: Long? = null,
)

@Serializable
data class LlmDecision(
    @SerialName("window_understanding") val windowUnderstanding: String,
    @SerialName("links_to_history") val linksToHistory: List<String> = emptyList(),
    @SerialName("open_threads_update") val openThreadsUpdate: OpenThreadsUpdate = OpenThreadsUpdate(),
    val intervene: Boolean,
    val advice: String? = null,
    /** 0 — silent log; 1 — regular; 2 — heads-up; 3 — sound+vibration */
    val urgency: Int = 0,
    val reason: String,
    @SerialName("memory_note") val memoryNote: String,
)

@Serializable
data class OpenThreadsUpdate(
    val closed: List<String> = emptyList(),
    val new: List<NewThread> = emptyList(),
    @SerialName("still_open") val stillOpen: List<String> = emptyList(),
)

@Serializable
data class NewThread(
    val id: String,
    val topic: String,
    val context: String = "",
    val due: Long? = null,
)
