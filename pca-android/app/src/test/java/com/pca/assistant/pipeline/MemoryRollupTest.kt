package com.pca.assistant.pipeline

import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.data.db.entity.WindowEntity
import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.OpenThreadsUpdate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [MemoryRollup] — the hour-summary builder used by
 * the WorkManager rollup job. Tests the spec §3.3 fix (B-4): memory_notes
 * from LLM decisions flow into the L1 hour summary.
 */
class MemoryRollupTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun transcript(ts: Long, text: String = "x", owner: Boolean = true, place: String? = null) =
        TranscriptEntity(
            ts = ts, text = text, textAnonymized = text,
            speakerId = if (owner) "owner" else "other",
            speakerScore = if (owner) 0.9f else 0.1f,
            isOwner = owner,
            locationLat = null, locationLng = null, placeLabel = place,
            confidence = 0.9f, language = "en",
        )

    private fun windowWith(memoryNote: String, ts: Long = 100): WindowEntity {
        val decision = LlmDecision(
            windowUnderstanding = "u",
            openThreadsUpdate = OpenThreadsUpdate(),
            intervene = false,
            urgency = 0,
            reason = "r",
            memoryNote = memoryNote,
        )
        return WindowEntity(
            startTs = ts, endTs = ts + 1000,
            rawContextJson = "{}", anonymizedContextJson = "{}",
            sentToLlm = true, llmProvider = "mock-local",
            llmResponseJson = json.encodeToString(LlmDecision.serializer(), decision),
            latencyMs = 12L,
        )
    }

    @Test fun `empty hour with no notes returns null`() {
        val out = MemoryRollup.aggregateHour(0, 1_000_000, emptyList(), emptyList(), json)
        assertNull(out)
    }

    @Test fun `transcripts only - summary built without notes section`() {
        val out = MemoryRollup.aggregateHour(
            hourStart = 0, hourEnd = 60 * 60_000L,
            transcripts = listOf(
                transcript(ts = 100, text = "hello world", place = "home"),
                transcript(ts = 200, text = "goodbye", owner = false),
            ),
            windows = emptyList(),
            json = json,
        )
        assertNotNull(out)
        assertTrue(out!!.summary.contains("owner=1"))
        assertTrue(out.summary.contains("others=1"))
        assertTrue(out.summary.contains("home"))
        // Memory notes empty → no "Notes:" prefix
        assertFalse(out.summary.contains("Notes:"))
        assertEquals("[]", out.memoryNotesJson)
    }

    @Test fun `windows with memory_notes are surfaced in the hour summary (B-4 fix)`() {
        val out = MemoryRollup.aggregateHour(
            hourStart = 0, hourEnd = 60 * 60_000L,
            transcripts = listOf(transcript(ts = 100)),
            windows = listOf(
                windowWith(memoryNote = "owner discussed lunch plans", ts = 100),
                windowWith(memoryNote = "owner mentioned a deadline tomorrow", ts = 1000),
            ),
            json = json,
        )
        assertNotNull(out)
        assertTrue("notes must appear in summary", out!!.summary.contains("lunch plans"))
        assertTrue(out.summary.contains("deadline tomorrow"))
        // memoryNotesJson is a real JSON array of strings.
        assertTrue(out.memoryNotesJson.startsWith("["))
        assertTrue(out.memoryNotesJson.contains("\"owner discussed lunch plans\""))
        assertTrue(out.memoryNotesJson.contains("\"owner mentioned a deadline tomorrow\""))
    }

    @Test fun `summary is built even when there are only memory_notes and no transcripts`() {
        val out = MemoryRollup.aggregateHour(
            hourStart = 0, hourEnd = 60 * 60_000L,
            transcripts = emptyList(),
            windows = listOf(windowWith("a single retained insight")),
            json = json,
        )
        assertNotNull(out)
        assertTrue(out!!.summary.contains("a single retained insight"))
    }

    @Test fun `memoryNotesJson roundtrips through kotlinx-serialization`() {
        val notes = listOf("first note", "second note with \"quotes\"")
        val out = MemoryRollup.aggregateHour(
            hourStart = 0, hourEnd = 60 * 60_000L,
            transcripts = listOf(transcript(ts = 100)),
            windows = notes.map { windowWith(it) },
            json = json,
        )!!
        val decoded = json.decodeFromString(
            ListSerializer(String.serializer()),
            out.memoryNotesJson,
        )
        assertEquals(notes, decoded)
    }

    @Test fun `malformed llmResponseJson is silently skipped not crash`() {
        val window = WindowEntity(
            startTs = 100, endTs = 200,
            rawContextJson = "{}", anonymizedContextJson = "{}",
            sentToLlm = true, llmProvider = "x",
            llmResponseJson = "not valid json at all {",
            latencyMs = 0L,
        )
        val out = MemoryRollup.aggregateHour(
            hourStart = 0, hourEnd = 60 * 60_000L,
            transcripts = listOf(transcript(ts = 100)),
            windows = listOf(window),
            json = json,
        )
        // Hour summary still produced from transcripts, malformed JSON ignored.
        assertNotNull(out)
        assertFalse(out!!.summary.contains("Notes:"))
    }

    @Test fun `extractMemoryNote returns null when response has blank note`() {
        val decision = LlmDecision(
            windowUnderstanding = "u",
            openThreadsUpdate = OpenThreadsUpdate(),
            intervene = false, urgency = 0,
            reason = "r", memoryNote = "",
        )
        val window = WindowEntity(
            startTs = 100, endTs = 200,
            rawContextJson = "{}", anonymizedContextJson = "{}",
            sentToLlm = true, llmProvider = "x",
            llmResponseJson = json.encodeToString(LlmDecision.serializer(), decision),
            latencyMs = 0L,
        )
        assertNull(MemoryRollup.extractMemoryNote(window, json))
    }

    @Test fun `extractMemoryNote returns null when llmResponseJson is null`() {
        val window = WindowEntity(
            startTs = 100, endTs = 200,
            rawContextJson = "{}", anonymizedContextJson = "{}",
            sentToLlm = false, llmProvider = null,
            llmResponseJson = null, latencyMs = null,
        )
        assertNull(MemoryRollup.extractMemoryNote(window, json))
    }

    @Test fun `places list is JSON-escaped`() {
        val out = MemoryRollup.aggregateHour(
            hourStart = 0, hourEnd = 60 * 60_000L,
            transcripts = listOf(
                transcript(ts = 100, place = "weird \"home\""),
                transcript(ts = 200, place = "back\\slash"),
            ),
            windows = emptyList(),
            json = json,
        )!!
        // Both must round-trip cleanly through json.parseToJsonElement
        val parsed = json.parseToJsonElement(out.locationsJson)
        assertTrue(parsed.toString().contains("weird"))
        assertTrue(parsed.toString().contains("back"))
    }
}
