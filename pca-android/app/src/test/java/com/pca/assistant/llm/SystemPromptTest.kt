package com.pca.assistant.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {

    @Test fun `evaluator prompt contains the key coherence rules from spec §5`() {
        val p = SystemPrompt.EVALUATOR
        assertTrue("PREVIOUS_WINDOW_DECISION rule missing", p.contains("PREVIOUS_WINDOW_DECISION"))
        assertTrue("OPEN_THREADS rule missing", p.contains("OPEN_THREADS"))
        assertTrue("INTERVENTION RULES section missing", p.contains("INTERVENTION RULES"))
        assertTrue("strict JSON cue missing", p.contains("strict JSON"))
        assertTrue("urgency 0..3 mention missing", p.contains("urgency"))
    }

    @Test fun `reply hint defaults to english for unknown language tags`() {
        assertEquals("Reply in English. The advice field must be in English.", SystemPrompt.replyHint("zh"))
        assertEquals("Reply in English. The advice field must be in English.", SystemPrompt.replyHint(""))
    }

    @Test fun `reply hint is russian when caller asks ru`() {
        val h = SystemPrompt.replyHint("ru")
        assertTrue("got: $h", h.contains("по-русски"))
    }

    @Test fun `reply hint is hebrew for either iw or he`() {
        val hIw = SystemPrompt.replyHint("iw")
        val hHe = SystemPrompt.replyHint("he")
        // Either Hebrew letter range marker is fine — what we want is that
        // both legacy ISO 639-1 "iw" and the modern "he" map to the same
        // Hebrew hint (Android resources use "iw", spec uses "he").
        assertEquals(hIw, hHe)
        assertTrue(hIw.any { it.code in 0x0590..0x05FF })
    }

    @Test fun `reply hint is case-insensitive`() {
        assertEquals(SystemPrompt.replyHint("ru"), SystemPrompt.replyHint("RU"))
        assertEquals(SystemPrompt.replyHint("en"), SystemPrompt.replyHint("EN-US"))
    }
}
