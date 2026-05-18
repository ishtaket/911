package com.pca.assistant.pipeline

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.llm.HttpBridgeProvider
import com.pca.assistant.llm.LlmProvider
import com.pca.assistant.llm.MockLocalProvider
import com.pca.assistant.llm.ProviderRouter
import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.OpenThreadsUpdate
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.settings.ProviderMode
import com.pca.assistant.testing.FakeHourSummaryDao
import com.pca.assistant.testing.FakeInterventionDao
import com.pca.assistant.testing.FakeLlmHealthDao
import com.pca.assistant.testing.FakeOpenThreadDao
import com.pca.assistant.testing.FakeOwnerDao
import com.pca.assistant.testing.FakeSettings
import com.pca.assistant.testing.FakeTranscriptDao
import com.pca.assistant.testing.FakeWindowDao
import com.pca.assistant.ui.notification.AdviceNotifier
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression test for B-7: a misbehaving LLM bridge returning out-of-range
 * urgency values must be clamped into the spec's documented 0..3 range
 * before being handed to NotificationCompat or stored as an intervention.
 */
class WindowProcessorUrgencyClampTest {

    private lateinit var processor: WindowProcessor
    private lateinit var interventionDao: FakeInterventionDao
    private lateinit var notifier: AdviceNotifier
    private lateinit var fakeSettings: FakeSettings

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Before fun setUp() {
        val transcriptDao = FakeTranscriptDao()
        val windowDao = FakeWindowDao()
        val ownerDao = FakeOwnerDao()
        val hourDao = FakeHourSummaryDao()
        val openThreadDao = FakeOpenThreadDao()
        interventionDao = FakeInterventionDao()
        val healthDao = FakeLlmHealthDao()
        fakeSettings = FakeSettings(
            FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE, bridgeUrl = "https://b.example.com")
        )
        val settings: AppSettings = mockk { every { flow } returns fakeSettings.flow }

        // Seed a non-skipped window so the pipeline reaches the urgency check.
        kotlinx.coroutines.runBlocking {
            transcriptDao.insert(
                TranscriptEntity(
                    ts = 1000, text = "hello", textAnonymized = "hello",
                    speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                    locationLat = null, locationLng = null, placeLabel = "home",
                    confidence = 0.9f, language = "en",
                )
            )
        }

        val bridge = mockk<HttpBridgeProvider>(relaxed = true)
        every { bridge.id } returns "http-bridge"
        coEvery { bridge.decide(any()) } returns LlmDecision(
            windowUnderstanding = "evil",
            openThreadsUpdate = OpenThreadsUpdate(),
            intervene = true,
            advice = "do the thing",
            urgency = 99,             // ← out of range
            reason = "test",
            memoryNote = "n",
        )

        notifier = mockk(relaxed = true)
        every { notifier.show(any(), any(), any()) } returns Unit

        val aggregator = WindowAggregator(transcriptDao, windowDao, hourDao, openThreadDao, ownerDao, Anonymizer())
        processor = WindowProcessor(
            aggregator = aggregator,
            router = ProviderRouter(MockLocalProvider(), bridge, healthDao, settings),
            windowDao = windowDao,
            interventionDao = interventionDao,
            openThreadDao = openThreadDao,
            settings = settings,
            notifier = notifier,
            json = json,
        )
    }

    @Test fun `out-of-range urgency is clamped to 3 before reaching notifier and DB`() = runTest {
        val urgencySlot = slot<Int>()
        every { notifier.show(any(), any(), capture(urgencySlot)) } returns Unit

        processor.process(from = 0, to = 300_000)

        assertEquals("notifier must see clamped urgency, got ${urgencySlot.captured}", 3, urgencySlot.captured)
        // And the intervention row must also have urgency=3 — not the bogus 99.
        assertEquals(1, interventionDao.store.value.size)
        assertEquals(3, interventionDao.store.value.single().urgency)
    }
}
