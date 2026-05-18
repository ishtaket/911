package com.pca.assistant.pipeline

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.dao.HourSummaryDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.WindowEntity
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.OpenThreadDto
import com.pca.assistant.llm.contract.WindowPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the L0 window payload from raw transcript rows + context
 * (location label, owner presence, time bounds) and prepares the rich
 * request that goes to the LLM evaluator (spec §3.3).
 *
 * SECURITY (PCA-S-1): Anonymisation happens HERE, at window-build time,
 * over the joined raw transcript. Doing it per-chunk in [com.pca.assistant.service.ListeningService]
 * caused token collisions across chunks (every chunk's PII restarted at
 * `[EMAIL_1]`), which meant the LLM received the same token referring to
 * different originals — a coherence + privacy bug. Anonymising over the
 * joined text produces a single coherent mapping for the whole window.
 * The mapping itself never leaves [collectSlice] — it lives on the stack
 * and is discarded the moment we return.
 */
@Singleton
class WindowAggregator @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val windowDao: WindowDao,
    private val hourDao: HourSummaryDao,
    private val openThreadDao: OpenThreadDao,
    private val ownerDao: OwnerDao,
    private val anonymizer: Anonymizer,
) {

    data class WindowSlice(
        val windowId: Long,
        val startTs: Long,
        val endTs: Long,
        val transcriptOriginal: String,
        val transcriptAnonymized: String,
        val isOwnerPresent: Boolean,
        val locationLabel: String?,
        val hadSpeech: Boolean,
    )

    /** Build an LlmRequest envelope from a [WindowEntity] row + the persisted history. */
    suspend fun buildRequest(slice: WindowSlice, replyLanguage: String): LlmRequest {
        val owner = ownerDao.get()
        val l3 = owner?.l3Summary?.ifBlank { "(no profile yet)" } ?: "(no profile yet)"

        // L1 = last 60 minutes of hour-summary rows concatenated.
        val hourEnd = slice.endTs
        val hourStart = hourEnd - 60 * 60_000L
        val l1 = hourDao.between(hourStart, hourEnd)
            .joinToString("\n") { "[${it.hourStart}] ${it.summary}" }
            .ifBlank { "(no recent hour summaries)" }

        // L2 = today's narrative (rolled up by the daily worker). The MVP uses
        // the same hour summaries as a stand-in until day_summaries is filled.
        val dayStart = hourEnd - 24 * 60 * 60_000L
        val l2 = hourDao.between(dayStart, hourEnd).takeLast(12)
            .joinToString("\n") { "[${it.hourStart}] ${it.summary}" }
            .ifBlank { "(no day narrative yet)" }

        val openThreads = openThreadDao.openThreads().map { th ->
            OpenThreadDto(
                id = th.id,
                topic = th.topic,
                context = th.context,
                openedAt = th.openedAt,
                due = th.due,
            )
        }

        return LlmRequest(
            systemPrompt = com.pca.assistant.llm.SystemPrompt.EVALUATOR,
            l3Profile = l3,
            l2Day = l2,
            l1Hour = l1,
            previousDecision = null,
            openThreads = openThreads,
            window = WindowPayload(
                windowId = slice.windowId,
                startTs = slice.startTs,
                endTs = slice.endTs,
                transcript = slice.transcriptAnonymized,
                locationLabel = slice.locationLabel,
                isOwnerPresent = slice.isOwnerPresent,
            ),
            instruction = "Evaluate the window per the rules; reply with strict JSON.",
            replyLanguage = replyLanguage,
        )
    }

    /** Read transcripts in [from..to) and stitch them into a slice. */
    suspend fun collectSlice(from: Long, to: Long): WindowSlice {
        val rows = transcriptDao.between(from, to)
        val combined = rows.joinToString(" ") { it.text }.trim()
        // SECURITY (PCA-S-1): re-anonymise over the joined raw text so the
        // token map is coherent for the whole window. The per-chunk values
        // already stored in `transcripts.textAnonymized` are kept for audit
        // but are intentionally NOT concatenated here.
        val anonResult = if (combined.isBlank()) {
            Anonymizer.AnonymizationResult(combined, emptyMap())
        } else {
            anonymizer.anonymize(combined)
        }
        val ownerPresent = rows.any { it.isOwner }
        val location = rows.lastOrNull { it.placeLabel != null }?.placeLabel
        return WindowSlice(
            windowId = 0L,
            startTs = from,
            endTs = to,
            transcriptOriginal = combined,
            transcriptAnonymized = anonResult.anonymized,
            isOwnerPresent = ownerPresent,
            locationLabel = location,
            hadSpeech = combined.isNotBlank(),
        )
    }

    suspend fun persistWindow(slice: WindowSlice, providerId: String?, sent: Boolean, latencyMs: Long?): Long {
        return windowDao.insert(
            WindowEntity(
                startTs = slice.startTs,
                endTs = slice.endTs,
                rawContextJson = """{"transcript":${jsonString(slice.transcriptOriginal)},"location":${jsonString(slice.locationLabel)},"is_owner":${slice.isOwnerPresent}}""",
                anonymizedContextJson = """{"transcript":${jsonString(slice.transcriptAnonymized)},"location":${jsonString(slice.locationLabel)},"is_owner":${slice.isOwnerPresent}}""",
                sentToLlm = sent,
                llmProvider = providerId,
                llmResponseJson = null,
                latencyMs = latencyMs,
                skipped = !slice.hadSpeech,
                skipReason = if (slice.hadSpeech) null else "no_speech",
            )
        )
    }

    private fun jsonString(s: String?): String =
        if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
