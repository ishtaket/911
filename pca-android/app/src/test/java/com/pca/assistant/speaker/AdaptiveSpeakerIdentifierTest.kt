package com.pca.assistant.speaker

import android.content.Context
import com.pca.assistant.models.ModelRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The adaptive router has to swap from synthetic to ONNX the moment the
 * model file lands on disk — without a service restart and without
 * re-injecting anything. These tests pin that contract.
 */
class AdaptiveSpeakerIdentifierTest {

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
        // Use a mocked ONNX impl so the test doesn't try to load a real
        // .onnx file off disk (we have none in unit tests).
        onnx = mockk(relaxed = true)
        every { onnx.id } returns "ecapa-tdnn-onnx"
        every { onnx.embeddingSize } returns 192
        every { onnx.embedding(any()) } returns FloatArray(192) { 0.01f }
        adaptive = AdaptiveSpeakerIdentifier(onnx, synthetic, registry)
    }

    @Test fun `with no model on disk all calls route to synthetic`() {
        assertEquals("synthetic-fallback", adaptive.id)
        assertEquals(SyntheticSpeakerIdentifier.EMBEDDING_SIZE, adaptive.embeddingSize)
        adaptive.embedding(ShortArray(8_000) { 100 })
        verify(exactly = 0) { onnx.embedding(any()) }
    }

    @Test fun `with the ONNX file present all calls route to ONNX`() {
        // Pretend the user successfully downloaded the ECAPA model.
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        assertEquals("ecapa-tdnn-onnx", adaptive.id)
        assertEquals(192, adaptive.embeddingSize)
        val emb = adaptive.embedding(ShortArray(8_000) { 100 })
        assertEquals(192, emb.size)
        verify(exactly = 1) { onnx.embedding(any()) }
    }

    @Test fun `routing decision is re-evaluated per call (no caching)`() {
        // Start with no model — synthetic
        adaptive.embedding(ShortArray(800) { 0 })
        verify(exactly = 0) { onnx.embedding(any()) }

        // Drop the model in
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(8192))
        }
        // Next call must pick ONNX without restart
        adaptive.embedding(ShortArray(800) { 0 })
        verify(exactly = 1) { onnx.embedding(any()) }
    }

    @Test fun `cosine + isOwner contract is inherited from the interface`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f, 0f)
        assertEquals(1f, adaptive.cosineSimilarity(a, b), 1e-6f)
        assertTrue(adaptive.isOwner(0.8f))
        assertTrue(!adaptive.isOwner(0.7f))
    }

    @Test fun `tiny ONNX file (under 1 KB) does NOT activate ONNX routing`() {
        // ModelRegistry.isReady guards against half-written / corrupt files.
        registry.fileFor(ModelRegistry.ECAPA_TDNN_ONNX).apply {
            parentFile?.mkdirs(); writeBytes(ByteArray(64))
        }
        assertEquals("synthetic-fallback", adaptive.id)
    }
}
