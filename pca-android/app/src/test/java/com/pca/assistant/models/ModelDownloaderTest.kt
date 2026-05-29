package com.pca.assistant.models

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var registry: ModelRegistry
    private lateinit var downloader: ModelDownloader

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val ctx: Context = mockk()
        every { ctx.filesDir } returns tmp.root
        registry = ModelRegistry(ctx)
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        downloader = ModelDownloader(client, registry)
    }

    @After fun tearDown() { server.shutdown() }

    private fun spec(name: String = "test-model", sha: String? = null) = ModelSpec(
        id = "test", filename = name,
        defaultUrl = server.url("/$name").toString(),
        approxMb = 1, sha256 = sha,
    )

    private fun bytesFilled(size: Int): ByteArray = ByteArray(size) { (it and 0xFF).toByte() }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun `successful download writes the file atomically and emits Done`() = runTest {
        val payload = bytesFilled(16 * 1024)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().write(payload))
        )

        val spec = spec()
        val events = downloader.download(spec).toList()
        val done = events.last()
        assertTrue("last event must be Done, got: $done", done is ModelDownloader.Progress.Done)
        val file = (done as ModelDownloader.Progress.Done).file
        assertEquals(payload.size.toLong(), file.length())
        assertFalse("temp .part file must be gone", file.resolveSibling(file.name + ".part").exists())
        assertEquals(registry.fileFor(spec), file)
    }

    @Test fun `installed file content matches the downloaded bytes`() = runTest {
        // Guards the rename -> Files.move/copy install path: the bytes that
        // land at the target must be exactly what was streamed.
        val payload = bytesFilled(96 * 1024)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().write(payload))
        )
        val spec = spec()
        val done = downloader.download(spec).toList().last()
        assertTrue("expected Done, got $done", done is ModelDownloader.Progress.Done)
        val file = (done as ModelDownloader.Progress.Done).file
        assertEquals(sha256Hex(payload), sha256Hex(file.readBytes()))
        assertFalse("temp .part must be gone", registry.modelsDir().list()?.contains(spec.filename + ".part") == true)
    }

    @Test fun `progress events monotonically increase to 100`() = runTest {
        val payload = bytesFilled(128 * 1024)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().write(payload))
        )
        val events = downloader.download(spec()).toList()
        val percents = events.filterIsInstance<ModelDownloader.Progress.Running>().map { it.percent }
        for (i in 1 until percents.size) {
            assertTrue("percent went backwards at $i: $percents", percents[i] >= percents[i - 1])
        }
        if (percents.isNotEmpty()) {
            assertEquals(100, percents.last())
        }
    }

    @Test fun `HTTP 404 emits Failed and writes nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val events = downloader.download(spec()).toList()
        val last = events.last()
        assertTrue(last is ModelDownloader.Progress.Failed)
        assertTrue((last as ModelDownloader.Progress.Failed).reason.contains("HTTP 404"))
        // No leftover .part or target file.
        val dir = registry.modelsDir()
        assertFalse(dir.list()?.contains("test-model") == true)
        assertFalse(dir.list()?.contains("test-model.part") == true)
    }

    @Test fun `connection drop emits Failed and cleans the partial file`() = runTest {
        // MockResponse needs an actual body for DISCONNECT_DURING_RESPONSE_BODY
        // to have something to truncate. We also claim a Content-Length larger
        // than the body so OkHttp realises the stream ended short and throws
        // an IOException instead of treating an empty truncated body as EOF.
        val partial = bytesFilled(64 * 1024)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", (partial.size * 4).toString())
                .setBody(Buffer().write(partial))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val events = downloader.download(spec()).toList()
        val last = events.last()
        assertTrue("last event must be Failed, got: $last", last is ModelDownloader.Progress.Failed)
        val dir = registry.modelsDir()
        assertFalse("part file leftover", dir.list()?.contains("test-model.part") == true)
    }

    @Test fun `if the target already exists Done is emitted without re-downloading`() = runTest {
        val target = registry.fileFor(spec())
        target.parentFile?.mkdirs()
        target.writeBytes(bytesFilled(4096))
        // Don't enqueue anything — if the downloader hits the server the test will hang.
        val events = downloader.download(spec()).toList()
        assertEquals(1, events.size)
        assertTrue(events.single() is ModelDownloader.Progress.Done)
        assertEquals(0, server.requestCount)
    }

    @Test fun `sha256 mismatch is detected and the file is rejected`() = runTest {
        val payload = bytesFilled(8192)
        val wrongHash = "0".repeat(64)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().write(payload))
        )
        val events = downloader.download(spec(sha = wrongHash)).toList()
        val last = events.last()
        assertTrue(last is ModelDownloader.Progress.Failed)
        assertTrue((last as ModelDownloader.Progress.Failed).reason.contains("sha256 mismatch"))
    }

    @Test fun `sha256 match passes verification`() = runTest {
        val payload = bytesFilled(8192)
        val hash = sha256Hex(payload)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().write(payload))
        )
        val events = downloader.download(spec(sha = hash)).toList()
        val last = events.last()
        assertNotNull(last)
        assertTrue("last event must be Done with sha match, got $last", last is ModelDownloader.Progress.Done)
    }

    @Test fun `overrideUrl wins over the default URL`() = runTest {
        // The default URL points at /test-model — never enqueue a response
        // for it; only enqueue for /override. If the downloader picked the
        // wrong URL the test would time out on the read.
        val payload = bytesFilled(1024)
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Length", payload.size.toString())
            .setBody(Buffer().write(payload)))

        val s = ModelSpec(
            id = "ovr", filename = "ovr.bin",
            defaultUrl = server.url("/never").toString(), approxMb = 1,
        )
        val override = server.url("/override").toString()
        val events = downloader.download(s, overrideUrl = override).toList()
        assertTrue(events.last() is ModelDownloader.Progress.Done)
        val recorded = server.takeRequest()
        assertEquals("/override", recorded.path)
    }
}
