package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.WindowPayload
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.testing.FakeSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * End-to-end check that the Android side of the bridge contract matches what
 * `bridge/server.py` consumes/returns. Uses OkHttp's MockWebServer so the
 * test exercises real HTTP framing, real JSON, and real timeout handling.
 */
class HttpBridgeProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: HttpBridgeProvider
    private lateinit var settings: AppSettings
    private lateinit var fakeSettings: FakeSettings

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Before fun start() {
        server = MockWebServer().apply { start() }
        fakeSettings = FakeSettings(
            FakeSettings.DEFAULT.copy(bridgeUrl = server.url("/").toString().trimEnd('/'))
        )
        settings = mockk { every { flow } returns fakeSettings.flow }
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
        provider = HttpBridgeProvider(client, json, settings)
    }

    @After fun stop() { server.shutdown() }

    private fun request(): LlmRequest = LlmRequest(
        systemPrompt = "p",
        l3Profile = "",
        l2Day = "",
        l1Hour = "",
        window = WindowPayload(
            windowId = 7L, startTs = 0L, endTs = 0L,
            transcript = "hi", locationLabel = "home", isOwnerPresent = true,
        ),
        instruction = "evaluate",
        replyLanguage = "en",
    )

    @Test fun `200 OK with spec-shaped JSON decodes into a decision`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "window_understanding": "owner at home",
                      "links_to_history": ["l1_x"],
                      "open_threads_update": {"closed": [], "new": [], "still_open": []},
                      "intervene": true,
                      "advice": "drink water",
                      "urgency": 1,
                      "reason": "long quiet stretch",
                      "memory_note": "owner present"
                    }
                    """.trimIndent()
                )
        )
        val d = provider.decide(request())
        assertEquals("owner at home", d.windowUnderstanding)
        assertEquals(true, d.intervene)
        assertEquals(1, d.urgency)
        assertEquals("drink water", d.advice)

        val taken = server.takeRequest()
        assertEquals("POST", taken.method)
        assertTrue(taken.path!!.endsWith("/decide"))
        val sent = taken.body.readUtf8()
        assertTrue("payload must carry reply_language", sent.contains("\"reply_language\""))
        assertTrue("payload must carry window.transcript", sent.contains("\"transcript\":\"hi\""))
    }

    @Test fun `bridge token is sent as the X-PCA-Token header when set`() = runTest {
        fakeSettings.mutate { it.copy(bridgeToken = "s3cr3t-token") }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"window_understanding":"ok","intervene":false,"urgency":0,
                 "reason":"r","memory_note":"n",
                 "open_threads_update":{"closed":[],"new":[],"still_open":[]}}
                """.trimIndent()
            )
        )
        provider.decide(request())
        val taken = server.takeRequest()
        assertEquals("s3cr3t-token", taken.getHeader("X-PCA-Token"))
    }

    @Test fun `no X-PCA-Token header when token is blank`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"window_understanding":"ok","intervene":false,"urgency":0,
                 "reason":"r","memory_note":"n",
                 "open_threads_update":{"closed":[],"new":[],"still_open":[]}}
                """.trimIndent()
            )
        )
        provider.decide(request())
        val taken = server.takeRequest()
        assertEquals(null, taken.getHeader("X-PCA-Token"))
    }

    @Test fun `non-200 surfaces as LlmProviderException with the HTTP code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("upstream"))
        val ex = try { provider.decide(request()); null } catch (e: LlmProviderException) { e }
        ex ?: fail("expected LlmProviderException")
        assertTrue("message: ${ex!!.message}", ex.message!!.contains("HTTP 503"))
    }

    @Test fun `bad JSON is reported as LlmProviderException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json {"))
        val ex = try { provider.decide(request()); null } catch (e: LlmProviderException) { e }
        ex ?: fail("expected LlmProviderException")
    }

    @Test fun `connection drop is wrapped as LlmProviderException`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val ex = try { provider.decide(request()); null } catch (e: LlmProviderException) { e }
        ex ?: fail("expected LlmProviderException on socket reset")
    }

    @Test fun `empty bridge URL fails fast`() = runTest {
        fakeSettings.mutate { it.copy(bridgeUrl = "   ") }
        val ex = try { provider.decide(request()); null } catch (e: LlmProviderException) { e }
        ex ?: fail("expected LlmProviderException for blank URL")
        assertTrue("message: ${ex!!.message}", ex.message!!.contains("Bridge URL"))
    }

    @Test fun `unknown response fields are tolerated (forward-compat)`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "window_understanding": "ok",
                  "intervene": false,
                  "urgency": 0,
                  "reason": "r",
                  "memory_note": "n",
                  "open_threads_update": {"closed": [], "new": [], "still_open": []},
                  "extras_for_v2": {"weather": "sunny"}
                }
                """.trimIndent()
            )
        )
        val d = provider.decide(request())
        assertEquals(false, d.intervene)
    }
}
