package com.pca.assistant.llm

import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.llm.contract.OpenThreadsUpdate
import com.pca.assistant.llm.contract.WindowPayload
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.testing.FakeLlmHealthDao
import com.pca.assistant.testing.FakeSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives [ProviderRouter] through every documented routing path:
 *   * MOCK mode                    → always the local mock provider
 *   * BRIDGE mode + healthy bridge → the bridge wins every call
 *   * BRIDGE mode + flaky bridge   → automatic fallback to mock with a
 *                                    health-counter cooldown (spec §3.4)
 *
 * Every call is asserted to leave at least one row in `llm_health` so a
 * future telemetry consumer can render the switch reason.
 */
class ProviderRouterTest {

    private lateinit var realMock: MockLocalProvider
    private lateinit var healthDao: FakeLlmHealthDao
    private lateinit var fakeSettings: FakeSettings
    private lateinit var settings: AppSettings

    @Before
    fun setUp() {
        realMock = MockLocalProvider()
        healthDao = FakeLlmHealthDao()
        fakeSettings = FakeSettings()
        settings = mockk { every { flow } returns fakeSettings.flow }
    }

    private fun sampleRequest(): LlmRequest = LlmRequest(
        systemPrompt = "p",
        l3Profile = "", l2Day = "", l1Hour = "",
        window = WindowPayload(
            windowId = 1, startTs = 0, endTs = 0,
            transcript = "hello there", locationLabel = null, isOwnerPresent = true,
        ),
        instruction = "evaluate",
        replyLanguage = "en",
    )

    /**
     * Builds a [HttpBridgeProvider] mock that forwards every `decide` call to
     * [behaviour]. The real implementation is `open` enough for mockk to wrap.
     */
    private fun bridgeStub(behaviour: suspend (LlmRequest) -> LlmDecision): HttpBridgeProvider {
        val bridge = mockk<HttpBridgeProvider>(relaxed = true)
        every { bridge.id } returns "http-bridge"
        coEvery { bridge.decide(any()) } coAnswers { behaviour(firstArg()) }
        return bridge
    }

    private fun happyDecision(): LlmDecision = LlmDecision(
        windowUnderstanding = "bridge speaks",
        openThreadsUpdate = OpenThreadsUpdate(),
        intervene = false,
        urgency = 0,
        reason = "test",
        memoryNote = "n",
    )

    @Test fun `mock mode routes everything to mock and logs success`() = runTest {
        fakeSettings.set(FakeSettings.DEFAULT.copy(providerMode = ProviderMode.MOCK))
        val router = ProviderRouter(realMock, bridgeStub { happyDecision() }, healthDao, settings)

        val (id, decision) = router.decide(sampleRequest())

        assertEquals("mock-local", id)
        assertTrue(decision.memoryNote.isNotBlank())
        assertEquals(1, healthDao.store.value.size)
        val row = healthDao.store.value.first()
        assertEquals("mock-local", row.provider)
        assertTrue(row.success)
        assertTrue("latencyMs=${row.latencyMs}", row.latencyMs >= 0)
    }

    @Test fun `bridge mode hits bridge first when healthy`() = runTest {
        fakeSettings.set(FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE))
        var calls = 0
        val router = ProviderRouter(
            realMock,
            bridgeStub { calls += 1; happyDecision() },
            healthDao, settings,
        )

        val (id, _) = router.decide(sampleRequest())

        assertEquals("http-bridge", id)
        assertEquals(1, calls)
        assertEquals(1, healthDao.store.value.size)
        assertTrue(healthDao.store.value.first().success)
    }

    @Test fun `single bridge failure transparently falls back to mock`() = runTest {
        fakeSettings.set(FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE))
        val router = ProviderRouter(
            realMock,
            bridgeStub { throw LlmProviderException("http-bridge", "boom") },
            healthDao, settings,
        )

        val (id, _) = router.decide(sampleRequest())

        assertEquals("mock-local", id)
        val log = healthDao.store.value.map { it.provider to it.success }
        assertTrue("bridge failure must be logged: $log", log.contains("http-bridge" to false))
        assertTrue("mock fallback must be logged: $log", log.contains("mock-local" to true))
    }

    @Test fun `three consecutive failures arm the cooldown - the 4th call skips the bridge`() = runTest {
        fakeSettings.set(FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE))
        var bridgeCalls = 0
        val router = ProviderRouter(
            realMock,
            bridgeStub { bridgeCalls += 1; throw LlmProviderException("http-bridge", "boom") },
            healthDao, settings,
        )

        repeat(3) { router.decide(sampleRequest()) }
        assertEquals(3, bridgeCalls)

        router.decide(sampleRequest())
        assertEquals("cooldown should suppress the 4th bridge attempt", 3, bridgeCalls)
    }

    @Test fun `a successful bridge call zeroes the failure counter`() = runTest {
        fakeSettings.set(FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE))
        var calls = 0
        val firstTwoFailThenOk = bridgeStub {
            calls += 1
            if (calls <= 2) throw LlmProviderException("http-bridge", "boom")
            happyDecision()
        }
        val router = ProviderRouter(realMock, firstTwoFailThenOk, healthDao, settings)
        repeat(3) { router.decide(sampleRequest()) }
        // After the 3rd (successful) call, the counter is back at 0 — even a
        // future failure does NOT immediately trip the breaker.
        router.decide(sampleRequest()) // calls == 4, fails
        router.decide(sampleRequest()) // calls == 5, fails
        // We've had 2 + 2 = 4 failures but not 3 in a row → still calling the bridge.
        assertEquals(5, calls)
    }

    @Test fun `unknown random exception from bridge is wrapped by callTracked and triggers fallback`() = runTest {
        fakeSettings.set(FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE))
        val router = ProviderRouter(
            realMock,
            bridgeStub { error("totally unrelated runtime explosion") },
            healthDao, settings,
        )
        // callTracked re-wraps everything as LlmProviderException, so the
        // fallback path catches it. The user gets a decision either way.
        val (id, _) = router.decide(sampleRequest())
        assertEquals("mock-local", id)
        // The bridge's failure was still logged so telemetry can surface it.
        val log = healthDao.store.value.map { it.provider to it.success }
        assertTrue("bridge failure must be logged: $log", log.contains("http-bridge" to false))
    }
}
