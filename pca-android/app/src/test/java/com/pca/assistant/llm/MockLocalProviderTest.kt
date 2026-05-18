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

class MockLocalProviderTest {

    private val provider = MockLocalProvider()

    private fun req(text: String, lang: String = "en", threads: List<OpenThreadDto> = emptyList()) = LlmRequest(
        systemPrompt = SystemPrompt.EVALUATOR,
        l3Profile = "owner profile",
        l2Day = "today narrative",
        l1Hour = "last hour",
        previousDecision = null,
        openThreads = threads,
        window = WindowPayload(
            windowId = 1,
            startTs = 0,
            endTs = 300_000,
            transcript = text,
            locationLabel = "home",
            isOwnerPresent = true,
        ),
        instruction = "evaluate",
        replyLanguage = lang,
    )

    @Test fun `silent by default`() = runTest {
        val d = provider.decide(req("we had lunch and went for a walk"))
        assertFalse(d.intervene)
        assertNull(d.advice)
        assertEquals(0, d.urgency)
        assertTrue(d.memoryNote.isNotBlank())
    }

    @Test fun `explicit invocation triggers reply`() = runTest {
        val d = provider.decide(req("hey assistant what about my call"))
        assertTrue(d.intervene)
        assertNotNull(d.advice)
        assertEquals(2, d.urgency)
    }

    @Test fun `promise opens a new thread`() = runTest {
        val d = provider.decide(req("I need to call John tomorrow"))
        assertTrue(d.intervene)
        assertEquals(1, d.urgency)
        assertEquals(1, d.openThreadsUpdate.new.size)
    }

    @Test fun `russian reply when language is ru`() = runTest {
        val d = provider.decide(req("эй ассистент,", lang = "ru"))
        assertTrue(d.intervene)
        assertTrue(d.advice!!.any { it.isLetter() && it.code in 0x0400..0x04FF })
    }

    @Test fun `mention of open thread closes it`() = runTest {
        val threads = listOf(
            OpenThreadDto(id = "t1", topic = "dentist", context = "", openedAt = 0)
        )
        val d = provider.decide(req("just left the dentist", threads = threads))
        assertEquals(listOf("t1"), d.openThreadsUpdate.closed)
        assertTrue(d.openThreadsUpdate.stillOpen.isEmpty())
    }
}
