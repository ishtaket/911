package com.pca.assistant.pipeline

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.dao.HourSummaryDao
import com.pca.assistant.data.db.dao.InterventionDao
import com.pca.assistant.data.db.dao.OpenThreadDao
import com.pca.assistant.data.db.dao.OwnerDao
import com.pca.assistant.data.db.dao.TranscriptDao
import com.pca.assistant.data.db.dao.WindowDao
import com.pca.assistant.data.db.entity.WindowEntity
import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.OpenThreadDto
import com.pca.assistant.llm.contract.WindowPayload
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private val interventionDao: InterventionDao,
) {

    /**
     * Schema for the per-window context blobs we persist into the `windows`
     * table. Using kotlinx-serialization (B-6 fix) instead of hand-rolled
     * string concatenation — the previous version mishandled control chars
     * inside transcripts (tabs, embedded quotes near edges, etc.).
     */
    @Serializable
    private data class ContextBlob(
        val transcript: String,
        val location: String?,
        @SerialName("is_owner") val isOwner: Boolean,
    )

    private val blobJson = Json {
        encodeDefaults = true
        explicitNulls = true
        // Tolerate older persisted responses or schema additions when we
        // decode the previous decision back from the windows table.
        ignoreUnknownKeys = true
    }

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

        // B-23 / spec §3.3 — feed the previous window's decision so the LLM
        // can honour "do not repeat advice you already gave". Best-effort:
        // if the last sent row has a malformed llmResponseJson, fall back to
        // null instead of failing the whole window build.
        val previous: LlmDecision? = windowDao.lastSent()?.llmResponseJson?.let { raw ->
            try {
                blobJson.decodeFromString(LlmDecision.serializer(), raw)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        // Spec §3.6 (PCA-B-37 fix): feed the owner's feedback on the
        // previous intervention back into the LLM context. `null` means
        // either there was no prior intervention or the owner hasn't
        // tapped a feedback button yet.
        val previousFeedback: String? = interventionDao.last()?.userFeedback

        return LlmRequest(
            systemPrompt = com.pca.assistant.llm.SystemPrompt.EVALUATOR,
            l3Profile = l3,
            l2Day = l2,
            l1Hour = l1,
            previousDecision = previous,
            previousFeedback = previousFeedback,
            openThreads = openThreads,
            window = WindowPayload(
                // B-21 fix: the Room-assigned auto-increment id isn't available
                // until after the LLM round-trip, but the LLM uses window_id
                // to correlate decisions across windows. Use startTs as the
                // stable identifier — unique per 5-min tick, deterministic.
                windowId = if (slice.windowId == 0L) slice.startTs else slice.windowId,
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

    /**
     * Persist a window row. [llmResponseJson] is optional — pass non-null
     * for the speech path (we have the LLM decision in hand) and null for
     * the skipped path. The single insert avoids the previous insert +
     * `byId(...)` + update sequence which could lose the response JSON on
     * a racing delete (B-22).
     */
    suspend fun persistWindow(
        slice: WindowSlice,
        providerId: String?,
        sent: Boolean,
        latencyMs: Long?,
        llmResponseJson: String? = null,
    ): Long {
        val raw = blobJson.encodeToString(
            ContextBlob.serializer(),
            ContextBlob(slice.transcriptOriginal, slice.locationLabel, slice.isOwnerPresent)
        )
        val anon = blobJson.encodeToString(
            ContextBlob.serializer(),
            ContextBlob(slice.transcriptAnonymized, slice.locationLabel, slice.isOwnerPresent)
        )
        return windowDao.insert(
            WindowEntity(
                startTs = slice.startTs,
                endTs = slice.endTs,
                rawContextJson = raw,
                anonymizedContextJson = anon,
                sentToLlm = sent,
                llmProvider = providerId,
                llmResponseJson = llmResponseJson,
                latencyMs = latencyMs,
                skipped = !slice.hadSpeech,
                skipReason = if (slice.hadSpeech) null else "no_speech",
            )
        )
    }
}
