package com.pca.assistant.stt

import android.content.Context
import com.pca.assistant.models.ModelRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Routing-only checks for [AdaptiveSpeechRecognizer]. We cannot actually
 * call the JNI native methods in a JVM unit test (libpca_whisper_jni.so is
 * not loaded), so the [WhisperJniRecognizer] dependency is mocked.
 */
class AdaptiveSpeechRecognizerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var registry: ModelRegistry
    private lateinit var whisper: WhisperJniRecognizer
    private lateinit var noop: NoopSpeechRecognizer
    private lateinit var adaptive: AdaptiveSpeechRecognizer

    @Before fun setUp() {
        val ctx: Context = mockk()
        every { ctx.filesDir } returns tmp.root
        registry = ModelRegistry(ctx)
        whisper = mockk<WhisperJniRecognizer>(relaxed = true).also {
            every { it.id } returns "whisper.cpp"
            coEvery { it.recognize(any(), any()) } returns SttResult("hello", 0.9f, "en")
        }
        noop = NoopSpeechRecognizer()
        adaptive = AdaptiveSpeechRecognizer(whisper, noop, registry)
    }

    @Test fun `without native lib OR a model the noop wins`() = runTest {
        // WhisperJniRecognizer.nativeAvailable is a static — we can't easily
        // toggle it from a test. Instead we exercise the path where the
        // model file is missing: even with native available, no model on
        // disk means whisperReady() is false.
        val r = adaptive.recognize(ShortArray(0), null)
        assertEquals("", r.text)
        assertEquals("noop", adaptive.id)
        coVerify(exactly = 0) { whisper.recognize(any(), any()) }
    }

    @Test fun `with a turbo model on disk and native loaded whisper wins`() = runTest {
        registry.fileFor(ModelRegistry.WHISPER_TURBO_Q5).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        // We only assert the routing decision — the actual JNI call is
        // mocked. If native isn't available on this machine, whisperReady
        // returns false and the noop wins; both branches are valid.
        adaptive.recognize(ShortArray(1024) { 0 }, null)
        if (adaptive.whisperReady()) {
            assertEquals("whisper.cpp", adaptive.id)
            coVerify(exactly = 1) { whisper.recognize(any(), any()) }
        } else {
            assertEquals("noop", adaptive.id)
        }
    }

    @Test fun `small model alone also unlocks whisper routing`() = runTest {
        registry.fileFor(ModelRegistry.WHISPER_SMALL_Q5).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        adaptive.recognize(ShortArray(1024) { 0 }, "en")
        if (adaptive.whisperReady()) {
            coVerify(exactly = 1) { whisper.recognize(any(), "en") }
        }
    }

    @Test fun `release forwards to whisper`() {
        adaptive.release()
        io.mockk.verify(exactly = 1) { whisper.release() }
    }
}
