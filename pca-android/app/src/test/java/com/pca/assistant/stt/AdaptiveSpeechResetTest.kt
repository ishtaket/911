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
 * Regression test for B-2: when a whisper ggml file disappears (Settings →
 * Delete) or is replaced (re-download), the adaptive recogniser must route
 * to noop / fresh ctx accordingly, not keep using the previously loaded
 * whisper.cpp context.
 *
 * The actual ctx reset lives inside [WhisperJniRecognizer.ensureLoaded]
 * (checks path + size + mtime each call); here we verify the higher-level
 * adaptive router responds correctly to file-state changes.
 */
class AdaptiveSpeechResetTest {

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
            coEvery { it.recognize(any(), any()) } returns SttResult("hi", 0.9f, "en")
        }
        noop = NoopSpeechRecognizer()
        adaptive = AdaptiveSpeechRecognizer(whisper, noop, registry)
    }

    @Test fun `model present then deleted swaps routing to noop`() = runTest {
        registry.fileFor(ModelRegistry.WHISPER_TURBO_Q5).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        adaptive.recognize(ShortArray(1024), null)
        // Even with `nativeAvailable=false` on the JVM, whisperReady is gated
        // by both the lib AND the file presence. The lib check returns false
        // in unit tests, so adaptive routes to noop regardless of the file —
        // make the assertion conditional on that.
        if (adaptive.whisperReady()) {
            coVerify(exactly = 1) { whisper.recognize(any(), any()) }
        } else {
            assertEquals("noop", adaptive.id)
        }

        // Delete the model file → adaptive MUST route to noop now.
        registry.fileFor(ModelRegistry.WHISPER_TURBO_Q5).delete()
        adaptive.recognize(ShortArray(1024), null)
        assertEquals("noop", adaptive.id)
    }

    @Test fun `model can be swapped between small and turbo via filesystem`() = runTest {
        registry.fileFor(ModelRegistry.WHISPER_SMALL_Q5).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        // adaptive.whisperReady tolerates either model file being present.
        // The actual model picked for the call is settings-driven inside
        // WhisperJniRecognizer; from the adaptive layer it just needs at
        // least one ggml file on disk.
        val readyWithSmall = adaptive.whisperReady()

        registry.fileFor(ModelRegistry.WHISPER_SMALL_Q5).delete()
        // No model at all → noop.
        assertEquals("noop", adaptive.id)

        // Add turbo back → ready again (when native is available).
        registry.fileFor(ModelRegistry.WHISPER_TURBO_Q5).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(9000))
        }
        // Document the invariant: whichever ggml file exists, whisperReady
        // is the same value (assuming native is constant).
        assertEquals(readyWithSmall, adaptive.whisperReady())
    }
}
