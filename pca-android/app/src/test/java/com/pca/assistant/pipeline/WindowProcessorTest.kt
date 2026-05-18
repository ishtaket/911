package com.pca.assistant.pipeline

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.llm.MockLocalProvider
import com.pca.assistant.llm.ProviderRouter
import com.pca.assistant.settings.AppSettings
import com.pca.assistant.testing.FakeHourSummaryDao
import com.pca.assistant.testing.FakeInterventionDao
import com.pca.assistant.testing.FakeLlmHealthDao
import com.pca.assistant.testing.FakeOpenThreadDao
import com.pca.assistant.testing.FakeOwnerDao
import com.pca.assistant.testing.FakeSettings
import com.pca.assistant.testing.FakeTranscriptDao
import com.pca.assistant.testing.FakeWindowDao
import com.pca.assistant.ui.notification.AdviceNotifier
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the end-to-end 5-minute cycle (spec §3.3) using all-fake DAOs +
 * the real [MockLocalProvider]. The notifier is mocked because it tries to
 * post a system notification.
 */
class WindowProcessorTest {

    private lateinit var transcriptDao: FakeTranscriptDao
    private lateinit var windowDao: FakeWindowDao
    private lateinit var interventionDao: FakeInterventionDao
    private lateinit var openThreadDao: FakeOpenThreadDao
    private lateinit var ownerDao: FakeOwnerDao
    private lateinit var hourDao: FakeHourSummaryDao
    private lateinit var healthDao: FakeLlmHealthDao
    private lateinit var fakeSettings: FakeSettings
    private lateinit var settings: AppSettings
    private lateinit var notifier: AdviceNotifier
    private lateinit var processor: WindowProcessor

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Before fun setUp() {
        transcriptDao = FakeTranscriptDao()
        windowDao = FakeWindowDao()
        interventionDao = FakeInterventionDao()
        openThreadDao = FakeOpenThreadDao()
        ownerDao = FakeOwnerDao()
        hourDao = FakeHourSummaryDao()
        healthDao = FakeLlmHealthDao()
        fakeSettings = FakeSettings()
        settings = mockk { every { flow } returns fakeSettings.flow }
        notifier = mockk(relaxed = true).also {
            justNotify(it)
        }
        val aggregator = WindowAggregator(transcriptDao, windowDao, hourDao, openThreadDao, ownerDao, Anonymizer())
        val router = ProviderRouter(
            mock = MockLocalProvider(),
            bridge = mockk(relaxed = true),
            healthDao = healthDao,
            settings = settings,
        )
        processor = WindowProcessor(
            aggregator = aggregator,
            router = router,
            interventionDao = interventionDao,
            openThreadDao = openThreadDao,
            settings = settings,
            notifier = notifier,
            json = json,
        )
    }

    private fun justNotify(n: AdviceNotifier) {
        every { n.show(any(), any(), any()) } returns Unit
    }

    private fun seed(text: String, ts: Long = 1_000L) {
        transcriptDao.insert(
            TranscriptEntity(
                ts = ts, text = text, textAnonymized = text,
                speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                locationLat = null, locationLng = null, placeLabel = "home",
                confidence = 0.8f, language = "en",
            )
        )
    }

    @Test fun `silent window is persisted as skipped and no LLM call is made`() = runTest {
        processor.process(from = 0, to = 300_000)
        val saved = windowDao.store.value
        assertEquals(1, saved.size)
        assertTrue(saved.first().skipped)
        assertEquals("no_speech", saved.first().skipReason)
        assertFalse(saved.first().sentToLlm)
        // No health row — the provider was never called.
        assertEquals(0, healthDao.store.value.size)
    }

    @Test fun `routine speech window writes the LLM response and stays silent`() = runTest {
        seed("we walked the dog and got coffee")
        processor.process(from = 0, to = 300_000)
        val saved = windowDao.store.value.single()
        assertTrue(saved.sentToLlm)
        assertEquals("mock-local", saved.llmProvider)
        assertNotNull(saved.llmResponseJson)
        assertEquals(1, healthDao.store.value.size)
        // No intervention row, no notification.
        assertEquals(0, interventionDao.store.value.size)
        verify(exactly = 0) { notifier.show(any(), any(), any()) }
    }

    @Test fun `explicit invocation produces an intervention and a notification`() = runTest {
        seed("hey assistant remind me to call back")
        processor.process(from = 0, to = 300_000)
        val intervention = interventionDao.store.value.single()
        assertEquals(2, intervention.urgency)
        verify(exactly = 1) { notifier.show(any(), any(), 2) }
    }

    @Test fun `promise opens a new thread that becomes visible to the next window`() = runTest {
        seed("I need to send the report by friday")
        processor.process(from = 0, to = 300_000)
        val threads = openThreadDao.openThreads()
        assertEquals(1, threads.size)
        assertEquals("promise", threads.first().topic)
    }

    @Test fun `LLM response JSON is persisted verbatim for audit (spec §7-2)`() = runTest {
        seed("how was your day")
        processor.process(from = 0, to = 300_000)
        val raw = windowDao.store.value.single().llmResponseJson
        assertNotNull(raw)
        assertTrue(raw!!.contains("memory_note"))
        assertTrue(raw.contains("window_understanding"))
    }
}
