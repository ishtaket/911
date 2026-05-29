package com.pca.assistant.security

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.llm.HttpBridgeProvider
import com.pca.assistant.llm.MockLocalProvider
import com.pca.assistant.llm.ProviderRouter
import com.pca.assistant.llm.contract.LlmDecision
import com.pca.assistant.llm.contract.NewThread
import com.pca.assistant.llm.contract.OpenThreadsUpdate
import com.pca.assistant.pipeline.WindowAggregator
import com.pca.assistant.pipeline.WindowProcessor
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for PCA-S-33 / S-34 / S-35: a misbehaving bridge — or a
 * compromised one — must not be able to fill the database, blow SQLite's
 * 999-parameter `IN (...)` limit, or wedge the notification system with a
 * gigantic advice string. The defensive caps in [WindowProcessor] enforce
 * spec §3.6 even when the LLM goes off-rails.
 */
class LlmDecisionBoundsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun buildProcessor(decision: LlmDecision): Triple<WindowProcessor, FakeInterventionDao, FakeOpenThreadDao> {
        val tDao = FakeTranscriptDao()
        val wDao = FakeWindowDao()
        val intDao = FakeInterventionDao()
        val oDao = FakeOpenThreadDao()
        val ownerDao = FakeOwnerDao()
        val hourDao = FakeHourSummaryDao()
        val healthDao = FakeLlmHealthDao()
        val fakeSettings = FakeSettings(
            FakeSettings.DEFAULT.copy(providerMode = ProviderMode.BRIDGE, bridgeUrl = "https://b.example.com")
        )
        val settings: AppSettings = mockk { every { flow } returns fakeSettings.flow }

        kotlinx.coroutines.runBlocking {
            tDao.insert(
                TranscriptEntity(
                    ts = 1000, text = "hi", textAnonymized = "hi",
                    speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                    locationLat = null, locationLng = null, placeLabel = "home",
                    confidence = 0.9f, language = "en",
                )
            )
        }

        val bridge = mockk<HttpBridgeProvider>(relaxed = true).also {
            every { it.id } returns "http-bridge"
            coEvery { it.decide(any()) } returns decision
        }
        val notifier = mockk<AdviceNotifier>(relaxed = true).also {
            every { it.show(any(), any(), any()) } returns Unit
        }

        val aggregator = WindowAggregator(tDao, wDao, hourDao, oDao, ownerDao, Anonymizer(), intDao)
        val processor = WindowProcessor(
            aggregator = aggregator,
            router = ProviderRouter(MockLocalProvider(), bridge, healthDao, settings),
            interventionDao = intDao,
            openThreadDao = oDao,
            settings = settings,
            notifier = notifier,
            json = json,
        )
        return Triple(processor, intDao, oDao)
    }

    @Test fun `runaway new-threads list is capped at 32 (PCA-S-33)`() = runTest {
        val tooMany = (1..1000).map { NewThread(id = "t$it", topic = "topic-$it", context = "ctx", due = null) }
        val (proc, _, oDao) = buildProcessor(
            LlmDecision(
                windowUnderstanding = "u",
                openThreadsUpdate = OpenThreadsUpdate(new = tooMany),
                intervene = false, urgency = 0,
                reason = "r", memoryNote = "n",
            )
        )
        proc.process(0, 300_000)
        assertEquals("at most 32 new threads per window", 32, oDao.store.value.size)
    }

    @Test fun `close-list larger than SQLite IN-limit is chunked (PCA-S-34)`() = runTest {
        // Seed 2000 open threads so we have real ids to close.
        val seedThreads = (1..2000).map { i ->
            com.pca.assistant.data.db.entity.OpenThreadEntity(
                id = "open-$i", topic = "t", openedAt = 0, openedInWindowId = 0,
                context = "", due = null, status = "open", lastMentionedAt = 0,
                relatedPeopleJson = "[]", relatedLocationsJson = "[]",
            )
        }
        val ids = seedThreads.map { it.id }
        val (proc, _, oDao) = buildProcessor(
            LlmDecision(
                windowUnderstanding = "u",
                openThreadsUpdate = OpenThreadsUpdate(closed = ids),
                intervene = false, urgency = 0,
                reason = "r", memoryNote = "n",
            )
        )
        kotlinx.coroutines.runBlocking { oDao.upsertAll(seedThreads) }
        proc.process(0, 300_000)
        // All 2000 should be marked closed despite the SQLite 999-param limit.
        assertEquals(0, oDao.openThreads().size)
    }

    @Test fun `gigantic advice is truncated at 4096 chars (PCA-S-35)`() = runTest {
        val giant = "x".repeat(100_000)
        val (proc, intDao, _) = buildProcessor(
            LlmDecision(
                windowUnderstanding = "u",
                openThreadsUpdate = OpenThreadsUpdate(),
                intervene = true,
                advice = giant,
                urgency = 1,
                reason = "y".repeat(50_000),
                memoryNote = "n",
            )
        )
        proc.process(0, 300_000)
        val row = intDao.store.value.single()
        assertEquals(4096, row.advice.length)
        assertEquals(1024, row.reason.length)
    }

    @Test fun `thread topic + id + context are length-capped`() = runTest {
        val giantString = "z".repeat(10_000)
        val (proc, _, oDao) = buildProcessor(
            LlmDecision(
                windowUnderstanding = "u",
                openThreadsUpdate = OpenThreadsUpdate(
                    new = listOf(
                        NewThread(id = giantString, topic = giantString, context = giantString, due = null)
                    )
                ),
                intervene = false, urgency = 0,
                reason = "r", memoryNote = "n",
            )
        )
        proc.process(0, 300_000)
        val saved = oDao.store.value.single()
        assertTrue("id len ${saved.id.length}", saved.id.length <= 128)
        assertTrue("topic len ${saved.topic.length}", saved.topic.length <= 256)
        assertTrue("ctx len ${saved.context.length}", saved.context.length <= 1024)
    }
}
