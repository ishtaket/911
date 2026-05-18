package com.pca.assistant.security

import android.content.Context
import com.pca.assistant.models.ModelDownloader
import com.pca.assistant.models.ModelRegistry
import com.pca.assistant.models.ModelSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Regression tests for `PCA-S-5`. Model weights are effectively executable
 * code in our pipeline (whisper.cpp + ONNX execute model-defined ops), so a
 * MITM-tampered .bin / .onnx is roughly equivalent to arbitrary code
 * substitution. The downloader therefore refuses cleartext to the public
 * internet — and the unit test pins exactly that.
 */
class ModelDownloaderUrlHardeningTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var downloader: ModelDownloader

    @Before fun setUp() {
        val ctx: Context = mockk()
        every { ctx.filesDir } returns tmp.root
        downloader = ModelDownloader(OkHttpClient(), ModelRegistry(ctx))
    }

    private fun specFor(url: String) = ModelSpec(
        id = "t", filename = "t.bin", defaultUrl = url, approxMb = 1,
    )

    @Test fun `https to public host is accepted (no actual network here)`() {
        // Only check the pre-flight gating result via internal helper.
        assertTrue(downloader.isAcceptableModelUrl("https://huggingface.co/x/y.bin"))
    }

    @Test fun `http to public host is refused`() {
        assertFalse(downloader.isAcceptableModelUrl("http://malicious.example.com/x.bin"))
    }

    @Test fun `http to loopback is allowed`() {
        assertTrue(downloader.isAcceptableModelUrl("http://127.0.0.1:8080/x.bin"))
        assertTrue(downloader.isAcceptableModelUrl("http://localhost:8080/x.bin"))
    }

    @Test fun `http to RFC-1918 ranges is allowed`() {
        assertTrue(downloader.isAcceptableModelUrl("http://10.0.0.5/x.bin"))
        assertTrue(downloader.isAcceptableModelUrl("http://192.168.1.50:8080/x.bin"))
        assertTrue(downloader.isAcceptableModelUrl("http://172.16.0.1/x.bin"))
        assertTrue(downloader.isAcceptableModelUrl("http://172.31.255.254/x.bin"))
    }

    @Test fun `addresses outside 172_16-31 are NOT treated as RFC-1918`() {
        assertFalse(downloader.isAcceptableModelUrl("http://172.15.0.1/x.bin"))
        assertFalse(downloader.isAcceptableModelUrl("http://172.32.0.1/x.bin"))
        assertFalse(downloader.isAcceptableModelUrl("http://172.100.0.1/x.bin"))
    }

    @Test fun `non-http schemes are refused`() {
        assertFalse(downloader.isAcceptableModelUrl("file:///etc/passwd"))
        assertFalse(downloader.isAcceptableModelUrl("ftp://mirror.example.com/x.bin"))
        assertFalse(downloader.isAcceptableModelUrl("javascript:alert(1)"))
        assertFalse(downloader.isAcceptableModelUrl("content://x/y"))
    }

    @Test fun `refused URLs emit Failed without touching the network`() = runTest {
        val events = downloader.download(specFor("http://attacker.example.com/x.bin")).toList()
        assertTrue(events.single() is ModelDownloader.Progress.Failed)
        val msg = (events.single() as ModelDownloader.Progress.Failed).reason
        assertTrue("reason was '$msg'", msg.contains("refused"))
    }

    @Test fun `mixed-case schemes do not bypass the check`() {
        assertFalse(downloader.isAcceptableModelUrl("HTTP://attacker.example.com/x.bin"))
        assertTrue(downloader.isAcceptableModelUrl("HTTPS://hf.example.com/x.bin"))
    }
}
