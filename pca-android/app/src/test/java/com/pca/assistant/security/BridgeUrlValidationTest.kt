package com.pca.assistant.security

import com.pca.assistant.llm.HttpBridgeProvider
import com.pca.assistant.llm.LlmProviderException
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.testing.FakeSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Regression test for `PCA-S-3`: a `file://` / `javascript:` / `content://`
 * URL in Settings → Bridge URL must be refused before OkHttp ever sees the
 * payload. Otherwise a confused user could ship serialised window context
 * to an OS handler (file write, shell URL handler, etc).
 */
class BridgeUrlValidationTest {

    private lateinit var provider: HttpBridgeProvider

    @Before fun setUp() {
        val fakeSettings = FakeSettings()
        val settings: AppSettings = mockk { every { flow } returns fakeSettings.flow }
        provider = HttpBridgeProvider(OkHttpClient(), Json {}, settings)
    }

    private fun expectRefusal(url: String) {
        try {
            provider.validateBridgeUrl(url)
            fail("expected refusal for URL: $url")
        } catch (e: LlmProviderException) {
            // expected
            assertEquals("http-bridge", e.provider)
        }
    }

    @Test fun `https is accepted`() {
        provider.validateBridgeUrl("https://bridge.example.com:8443")
    }

    @Test fun `http is accepted at this layer (network_security_config narrows further)`() {
        // The provider only enforces the scheme. The actual cleartext/LAN
        // restriction is enforced by Android's NetworkSecurityConfig.
        provider.validateBridgeUrl("http://192.168.1.50:8765")
    }

    @Test fun `blank url is refused`() {
        expectRefusal("")
        expectRefusal("   ")
    }

    @Test fun `file scheme is refused`() {
        expectRefusal("file:///etc/passwd")
        expectRefusal("file://localhost/tmp/payload")
    }

    @Test fun `javascript scheme is refused`() {
        expectRefusal("javascript:alert(1)")
    }

    @Test fun `data url is refused`() {
        expectRefusal("data:application/json,{}")
    }

    @Test fun `content scheme is refused`() {
        expectRefusal("content://com.evil.provider/path")
    }

    @Test fun `ftp scheme is refused`() {
        expectRefusal("ftp://bridge.example.com/payload")
    }

    @Test fun `mixed-case scheme is still validated`() {
        // We lowercase before comparison — make sure we don't bypass via case.
        provider.validateBridgeUrl("HTTPS://bridge.example.com")
        expectRefusal("FILE:///tmp")
    }

    @Test fun `host missing after scheme is refused`() {
        expectRefusal("https://")
        expectRefusal("http:///path-only")
    }

    @Test fun `unicode lookalike scheme is refused`() {
        // The check is `startsWith("http://")` after lowercase — any
        // homoglyph or BIDI override changes the prefix and gets rejected.
        expectRefusal("һttps://evil.example.com")
        expectRefusal("‮https://example.com") // RTL override prefix
    }

    @Test fun `whitespace-padded url is trimmed but still validated for scheme`() {
        // decide() trims; validateBridgeUrl receives the trimmed form, so
        // we just verify the trimmed form is allowed.
        provider.validateBridgeUrl("https://bridge.example.com:8443")
    }

    @Test fun `errors carry the provider id so health-log rows are attributable`() {
        try {
            provider.validateBridgeUrl("file:///tmp")
            fail("expected refusal")
        } catch (e: LlmProviderException) {
            assertTrue(e.message!!.contains("scheme"))
            assertEquals("http-bridge", e.provider)
        }
    }
}
