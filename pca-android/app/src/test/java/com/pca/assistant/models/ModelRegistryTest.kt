package com.pca.assistant.models

import android.content.Context
import com.pca.assistant.settings.SttModelChoice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelRegistryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var registry: ModelRegistry

    @Before fun setUp() {
        ctx = mockk()
        every { ctx.filesDir } returns tmp.root
        registry = ModelRegistry(ctx)
    }

    @Test fun `modelsDir is auto-created on first access`() {
        val d = registry.modelsDir()
        assertTrue(d.exists())
        assertTrue(d.isDirectory)
        assertEquals("models", d.name)
    }

    @Test fun `fileFor returns a child of modelsDir`() {
        val f = registry.fileFor(ModelRegistry.WHISPER_TURBO_Q5)
        assertEquals("ggml-large-v3-turbo-q5_0.bin", f.name)
        assertEquals(registry.modelsDir(), f.parentFile)
    }

    @Test fun `isReady is false for missing and present-but-tiny files`() {
        val spec = ModelRegistry.WHISPER_TURBO_Q5
        assertFalse(registry.isReady(spec))
        // 100 bytes — clearly not a real model.
        registry.fileFor(spec).apply { parentFile?.mkdirs(); writeBytes(ByteArray(100)) }
        assertFalse(registry.isReady(spec))
        // 2 KB — over the 1 KB sanity threshold.
        registry.fileFor(spec).writeBytes(ByteArray(2048))
        assertTrue(registry.isReady(spec))
    }

    @Test fun `whisperFor maps every SttModelChoice to a concrete spec`() {
        assertEquals(ModelRegistry.WHISPER_SMALL_Q5, registry.whisperFor(SttModelChoice.WHISPER_SMALL_Q5))
        assertEquals(ModelRegistry.WHISPER_TURBO_Q5, registry.whisperFor(SttModelChoice.WHISPER_TURBO_Q5))
        // The combined IVRIT mode still loads the same base turbo model as primary…
        assertEquals(ModelRegistry.WHISPER_TURBO_Q5, registry.whisperFor(SttModelChoice.WHISPER_TURBO_PLUS_IVRIT))
        // …and even the legacy "Android STT" choice resolves to turbo so the
        // service is functional once the user downloads it.
        assertEquals(ModelRegistry.WHISPER_TURBO_Q5, registry.whisperFor(SttModelChoice.ANDROID_BUILT_IN))
    }

    @Test fun `ivritFor returns the booster only for the combined choice`() {
        assertNotNull(registry.ivritFor(SttModelChoice.WHISPER_TURBO_PLUS_IVRIT))
        assertNull(registry.ivritFor(SttModelChoice.WHISPER_TURBO_Q5))
        assertNull(registry.ivritFor(SttModelChoice.WHISPER_SMALL_Q5))
        assertNull(registry.ivritFor(SttModelChoice.ANDROID_BUILT_IN))
    }

    @Test fun `spec defaults look sane`() {
        for (spec in listOf(
            ModelRegistry.WHISPER_SMALL_Q5,
            ModelRegistry.WHISPER_TURBO_Q5,
            ModelRegistry.IVRIT_TURBO_Q5,
            ModelRegistry.ECAPA_TDNN_ONNX,
        )) {
            assertTrue("id blank for ${spec.filename}", spec.id.isNotBlank())
            assertTrue("filename blank for ${spec.id}", spec.filename.isNotBlank())
            assertTrue("URL not absolute for ${spec.id}", spec.defaultUrl.startsWith("https://"))
            assertTrue("approxMb non-positive for ${spec.id}", spec.approxMb > 0)
        }
    }

    @Test fun `every spec has a unique id and filename`() {
        val specs = listOf(
            ModelRegistry.WHISPER_SMALL_Q5,
            ModelRegistry.WHISPER_TURBO_Q5,
            ModelRegistry.IVRIT_TURBO_Q5,
            ModelRegistry.ECAPA_TDNN_ONNX,
        )
        assertEquals(specs.size, specs.map { it.id }.toSet().size)
        assertEquals(specs.size, specs.map { it.filename }.toSet().size)
    }

    @Test fun `filenames are filesystem-safe (no directory separators)`() {
        for (spec in listOf(
            ModelRegistry.WHISPER_SMALL_Q5,
            ModelRegistry.WHISPER_TURBO_Q5,
            ModelRegistry.IVRIT_TURBO_Q5,
            ModelRegistry.ECAPA_TDNN_ONNX,
        )) {
            assertFalse("path separator in ${spec.filename}", spec.filename.contains(File.separatorChar))
            assertFalse("path traversal in ${spec.filename}", spec.filename.contains(".."))
        }
    }
}
