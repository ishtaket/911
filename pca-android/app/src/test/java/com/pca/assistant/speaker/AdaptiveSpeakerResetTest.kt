package com.pca.assistant.speaker

import android.content.Context
import com.pca.assistant.models.ModelRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Regression test for B-1: the adaptive router must drop back to synthetic
 * the moment the ECAPA model file disappears (e.g. user tapped Settings →
 * Delete or did a full wipe), even if a previous call had already triggered
 * an ONNX session load. Otherwise the in-memory ONNX session keeps serving
 * embeddings from the deleted model.
 *
 * The actual session reset lives in `OnnxEcapaIdentifier.ensureSession`;
 * here we verify the adaptive router does the right routing-level thing
 * (synthetic), and that a re-appearance of the file resumes ONNX routing
 * without restarting the app.
 */
class AdaptiveSpeakerResetTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var registry: ModelRegistry
    private lateinit var synthetic: SyntheticSpeakerIdentifier
    private lateinit var onnx: OnnxEcapaIdentifier
    private lateinit var adaptive: AdaptiveSpeakerIdentifier

    @Before fun setUp() {
        val ctx: Context = mockk()
        every { ctx.filesDir } returns tmp.root
        registry = ModelRegistry(ctx)
        synthetic = SyntheticSpeakerIdentifier()
        onnx = mockk(relaxed = true).also {
            every { it.id } returns "ecapa-tdnn-onnx"
            every { it.embeddingSize } returns 192
            every { it.embedding(any()) } returns FloatArray(192) { 0.01f }
        }
        adaptive = AdaptiveSpeakerIdentifier(onnx, synthetic, registry)
    }

    @Test fun `file present then wiped routes ONNX-then-synthetic`() {
        // 1. Model present → ONNX route.
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        assertEquals("ecapa-tdnn-onnx", adaptive.id)
        adaptive.embedding(ShortArray(8_000) { 0 })
        verify(exactly = 1) { onnx.embedding(any()) }

        // 2. Wipe the file (simulates Settings → Delete or full-wipe).
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).delete()

        // 3. Next call routes to synthetic without restarting the process.
        assertEquals("synthetic-fallback", adaptive.id)
        adaptive.embedding(ShortArray(8_000) { 0 })
        // Still only one call to onnx — the new one went to synthetic.
        verify(exactly = 1) { onnx.embedding(any()) }
    }

    @Test fun `file re-appears after delete resumes ONNX routing`() {
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        adaptive.embedding(ShortArray(800) { 0 })          // → onnx
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).delete()
        adaptive.embedding(ShortArray(800) { 0 })          // → synthetic
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).apply { writeBytes(ByteArray(9000)) }
        adaptive.embedding(ShortArray(800) { 0 })          // → onnx again

        verify(exactly = 2) { onnx.embedding(any()) }
    }
}
