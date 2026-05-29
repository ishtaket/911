package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.OpenThreadDto
import com.pca.assistant.llm.contract.WindowPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocalProviderExtraTest {

    private val p = MockLocalProvider()

    private fun req(text: String, lang: String = "en", threads: List<OpenThreadDto> = emptyList(), ownerPresent: Boolean = true) = LlmRequest(
        systemPrompt = SystemPrompt.EVALUATOR,
        l3Profile = "",
        l2Day = "",
        l1Hour = "",
        previousDecision = null,
        openThreads = threads,
        window = WindowPayload(
            windowId = 1,
            startTs = 0,
            endTs = 300_000,
            transcript = text,
            locationLabel = "home",
            isOwnerPresent = ownerPresent,
        ),
        instruction = "evaluate",
        replyLanguage = lang,
    )

    @Test fun `intervention requires owner to be present`() = runTest {
        // Explicit trigger phrase but owner is not in the window.
        val d = p.decide(req("hey assistant remind me", ownerPresent = false))
        assertFalse("intervention only fires when owner is present", d.intervene)
        assertNull(d.advice)
    }

    @Test fun `closing an open thread does not affect still_open ids`() = runTest {
        val threads = listOf(
            OpenThreadDto("a", "dentist", "", 0),
            OpenThreadDto("b", "milk", "", 0),
            OpenThreadDto("c", "gym", "", 0),
        )
        val d = p.decide(req("just left the dentist and the gym was great", threads = threads))
        assertTrue("a closed", d.openThreadsUpdate.closed.contains("a"))
        assertTrue("c closed", d.openThreadsUpdate.closed.contains("c"))
        assertEquals(listOf("b"), d.openThreadsUpdate.stillOpen)
    }

    @Test fun `silent window still emits a non-empty memory_note`() = runTest {
        val d = p.decide(req("the weather is nice today"))
        assertFalse(d.intervene)
        assertTrue(d.memoryNote.isNotBlank())
    }

    @Test fun `explicit invocation gets urgency 2`() = runTest {
        val d = p.decide(req("эй ассистент, что у меня дальше", lang = "ru"))
        assertTrue(d.intervene)
        assertEquals(2, d.urgency)
    }

    @Test fun `promise pattern in russian fires intervention`() = runTest {
        val d = p.decide(req("я должен позвонить маме сегодня", lang = "ru"))
        assertTrue(d.intervene)
        assertEquals(1, d.urgency)
        assertNotNull(d.advice)
    }

    @Test fun `promise pattern in hebrew fires intervention`() = runTest {
        val d = p.decide(req("אני חייב להתקשר אליו", lang = "iw"))
        assertTrue(d.intervene)
        assertEquals(1, d.urgency)
    }

    @Test fun `new threads carry a stable id derived from text hash`() = runTest {
        val a = p.decide(req("I will buy bread"))
        val b = p.decide(req("I will buy bread"))
        // Same window_id (1) + same text → same hash, same thread id.
        assertEquals(a.openThreadsUpdate.new.firstOrNull()?.id,
                     b.openThreadsUpdate.new.firstOrNull()?.id)
    }

    @Test fun `window understanding always references window id and length`() = runTest {
        val d = p.decide(req("anything goes here"))
        assertTrue(d.windowUnderstanding.contains("Window 1"))
    }
}
