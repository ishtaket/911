package com.pca.assistant.pipeline

import com.pca.assistant.data.db.entity.HourSummaryEntity
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.data.db.entity.WindowEntity
import com.pca.assistant.llm.contract.LlmDecision
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure (Android-free) rollup helpers used by the WorkManager workers.
 *
 * Extracted so the hour/day summarisation logic is unit-testable without
 * having to spin up WorkManager + Hilt — those layers are now thin wrappers
 * that read from the DAOs, hand the rows to one of these functions, and
 * persist the result.
 *
 * SPEC FIX (B-4): hour summaries now also include `memory_note` values
 * pulled out of each window's persisted [LlmDecision]. The spec §3.3 says
 * "memory_note from the response is added to the L1 accumulator" — before
 * this we stored memory_notes inside `windows.llmResponseJson` but never
 * surfaced them into the hour layer, breaking the L0 → L1 → L2 → L3 chain.
 */
object MemoryRollup {

    /**
     * Build the L1 hour-summary row for the half-open `[hourStart, hourEnd)`
     * range. Returns null if the slice has nothing worth recording (no
     * transcripts and no LLM-emitted notes).
     */
    fun aggregateHour(
        hourStart: Long,
        hourEnd: Long,
        transcripts: List<TranscriptEntity>,
        windows: List<WindowEntity>,
        json: Json = DEFAULT_JSON,
    ): HourSummaryEntity? {
        val memoryNotes = windows.mapNotNull { extractMemoryNote(it, json) }
        if (transcripts.isEmpty() && memoryNotes.isEmpty()) return null

        val ownerLines = transcripts.count { it.isOwner }
        val otherLines = transcripts.size - ownerLines
        val places = transcripts.mapNotNull { it.placeLabel }.distinct().take(6)
        val sampleText = transcripts.takeLast(8).joinToString(" · ") { it.text.take(120) }

        val summary = buildString {
            append("Hour ${formatHour(hourStart)}: ")
            append("owner=$ownerLines lines, others=$otherLines lines. ")
            append("Places: ${if (places.isEmpty()) "—" else places.joinToString()}. ")
            if (memoryNotes.isNotEmpty()) {
                append("Notes: ${memoryNotes.joinToString(" · ") { it.take(160) }}. ")
            }
            if (sampleText.isNotBlank()) append("Recent: $sampleText")
        }

        return HourSummaryEntity(
            hourStart = hourStart,
            hourEnd = hourEnd,
            summary = summary,
            memoryNotesJson = json.encodeToString(
                ListSerializer(String.serializer()),
                memoryNotes,
            ),
            locationsJson = places.joinToString(prefix = "[", postfix = "]") { "\"${jsonEscape(it)}\"" },
            peopleJson = "[]",
        )
    }

    /** Extract the `memory_note` field from a window's persisted LLM response. */
    fun extractMemoryNote(window: WindowEntity, json: Json = DEFAULT_JSON): String? {
        val raw = window.llmResponseJson ?: return null
        if (raw.isBlank()) return null
        return try {
            val decision = json.decodeFromString(LlmDecision.serializer(), raw)
            decision.memoryNote.takeIf { it.isNotBlank() }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            // kotlinx-serialization throws this for malformed JSON in some paths.
            null
        }
    }

    private fun formatHour(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(ts))

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private val DEFAULT_JSON = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
