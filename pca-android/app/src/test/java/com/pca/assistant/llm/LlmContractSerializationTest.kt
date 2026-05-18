package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.NewThread
import com.pca.assistant.llm.contract.OpenThreadDto
import com.pca.assistant.llm.contract.OpenThreadsUpdate
import com.pca.assistant.llm.contract.WindowPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format between the Android app and the codex / Gemini bridge
 * (spec §5) is normative. These tests pin both directions of the JSON
 * contract so a careless rename of a Kotlin field can't silently break
 * the bridge.
 */
class LlmContractSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun sampleRequest(): LlmRequest = LlmRequest(
        systemPrompt = "you are an assistant",
        l3Profile = "owner profile",
        l2Day = "day narrative",
        l1Hour = "hour summary",
        previousDecision = null,
        openThreads = listOf(
            OpenThreadDto(id = "t1", topic = "call dentist", context = "left voicemail", openedAt = 100L, due = 200L),
        ),
        window = WindowPayload(
            windowId = 42L,
            startTs = 1_000L,
            endTs = 1_300_000L,
            transcript = "hello world",
            locationLabel = "home",
            isOwnerPresent = true,
        ),
        instruction = "evaluate",
        replyLanguage = "ru",
    )

    private fun sampleDecision(): LlmDecision = LlmDecision(
        windowUnderstanding = "owner is at home",
        linksToHistory = listOf("l1_2024-01-01_10"),
        openThreadsUpdate = OpenThreadsUpdate(
            closed = listOf("t1"),
            new = listOf(NewThread(id = "t2", topic = "buy milk", context = "before 8pm", due = 500L)),
            stillOpen = listOf("t3"),
        ),
        intervene = true,
        advice = "remember to call back",
        urgency = 2,
        reason = "open thread still pending",
        memoryNote = "spoke about errands",
    )

    @Test fun `request roundtrips`() {
        val original = sampleRequest()
        val encoded = json.encodeToString(LlmRequest.serializer(), original)
        val decoded = json.decodeFromString(LlmRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test fun `request uses snake_case wire names`() {
        val encoded = json.encodeToString(LlmRequest.serializer(), sampleRequest())
        // Spot-check the names the bridge contract documents.
        assertTrue(encoded.contains("\"system_prompt\""))
        assertTrue(encoded.contains("\"l3_profile\""))
        assertTrue(encoded.contains("\"l2_day\""))
        assertTrue(encoded.contains("\"l1_hour\""))
        assertTrue(encoded.contains("\"open_threads\""))
        assertTrue(encoded.contains("\"window\""))
        assertTrue(encoded.contains("\"reply_language\""))
        assertTrue(encoded.contains("\"window_id\""))
        assertTrue(encoded.contains("\"start_ts\""))
        assertTrue(encoded.contains("\"end_ts\""))
        assertTrue(encoded.contains("\"location_label\""))
        assertTrue(encoded.contains("\"is_owner_present\""))
    }

    @Test fun `decision uses snake_case wire names from spec §5`() {
        val encoded = json.encodeToString(LlmDecision.serializer(), sampleDecision())
        assertTrue(encoded.contains("\"window_understanding\""))
        assertTrue(encoded.contains("\"links_to_history\""))
        assertTrue(encoded.contains("\"open_threads_update\""))
        assertTrue(encoded.contains("\"still_open\""))
        assertTrue(encoded.contains("\"memory_note\""))
        // The spec uses these flags verbatim:
        assertTrue(encoded.contains("\"intervene\""))
        assertTrue(encoded.contains("\"urgency\""))
        assertTrue(encoded.contains("\"reason\""))
    }

    @Test fun `decoder tolerates unknown fields from a future bridge`() {
        val raw = """
            {
              "window_understanding": "x",
              "intervene": false,
              "urgency": 0,
              "reason": "quiet",
              "memory_note": "n",
              "links_to_history": [],
              "open_threads_update": {"closed": [], "new": [], "still_open": []},
              "future_field_we_dont_know_about": 1234
            }
        """.trimIndent()
        val d = json.decodeFromString(LlmDecision.serializer(), raw)
        assertFalse(d.intervene)
        assertNull(d.advice)
    }

    @Test fun `null advice and absent due decode cleanly`() {
        val raw = """
            {
              "window_understanding": "owner walking",
              "intervene": false,
              "urgency": 0,
              "reason": "routine",
              "memory_note": "walk",
              "open_threads_update": {"closed": [], "new": [], "still_open": []}
            }
        """.trimIndent()
        val d = json.decodeFromString(LlmDecision.serializer(), raw)
        assertNull(d.advice)
        assertEquals(0, d.urgency)
        assertEquals("walk", d.memoryNote)
    }

    @Test fun `new thread without due is accepted`() {
        val raw = """
            {
              "window_understanding": "x", "intervene": true, "urgency": 1,
              "reason": "r", "memory_note": "n",
              "advice": "do something",
              "open_threads_update": {
                "closed": [],
                "new": [{"id": "n1", "topic": "remind"}],
                "still_open": []
              }
            }
        """.trimIndent()
        val d = json.decodeFromString(LlmDecision.serializer(), raw)
        assertEquals(1, d.openThreadsUpdate.new.size)
        val nt = d.openThreadsUpdate.new.first()
        assertEquals("n1", nt.id)
        assertEquals("remind", nt.topic)
        assertNull(nt.due)
        assertEquals("", nt.context)
    }

    @Test fun `previous decision is included when provided`() {
        val req = sampleRequest().copy(previousDecision = sampleDecision())
        val encoded = json.encodeToString(LlmRequest.serializer(), req)
        assertTrue(encoded.contains("\"previous_decision\""))
        val decoded = json.decodeFromString(LlmRequest.serializer(), encoded)
        assertEquals(req.previousDecision, decoded.previousDecision)
    }
}
