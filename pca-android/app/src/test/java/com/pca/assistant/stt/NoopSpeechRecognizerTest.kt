package com.pca.assistant.stt

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NoopSpeechRecognizerTest {

    private val r = NoopSpeechRecognizer()

    @Test fun `returns empty string and zero confidence`() = runTest {
        val out = r.recognize(ShortArray(0), null)
        assertEquals("", out.text)
        assertEquals(0f, out.confidence, 0f)
    }

    @Test fun `preserves caller-supplied language hint`() = runTest {
        val out = r.recognize(ShortArray(8), "iw-IL")
        assertEquals("iw-IL", out.detectedLanguage)
    }

    @Test fun `id is stable for telemetry`() {
        assertEquals("noop", r.id)
    }
}
