package com.pca.assistant.security

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.llm.SystemPrompt
import com.pca.assistant.llm.contract.LlmRequest
import com.pca.assistant.pipeline.WindowAggregator
import com.pca.assistant.testing.FakeHourSummaryDao
import com.pca.assistant.testing.FakeOpenThreadDao
import com.pca.assistant.testing.FakeOwnerDao
import com.pca.assistant.testing.FakeTranscriptDao
import com.pca.assistant.testing.FakeWindowDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Privacy invariants for the JSON sent to the LLM (spec §3.5, §7.2).
 *
 * The end-to-end claim is: the bytes leaving the device carry only the
 * anonymised transcript, a bucketed location *label*, and metadata.
 * Raw lat/lng, raw phone numbers, raw emails, and the owner's voice
 * embedding never appear in the serialised payload.
 *
 * These tests assert the structural invariant by serialising a built
 * request and grepping the bytes for things that must NOT be there.
 */
class LlmPayloadPrivacyTest {

    private lateinit var transcriptDao: FakeTranscriptDao
    private lateinit var aggregator: WindowAggregator
    private val json = Json {
        ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false
    }

    @Before fun setUp() {
        transcriptDao = FakeTranscriptDao()
        aggregator = WindowAggregator(
            transcriptDao = transcriptDao,
            windowDao = FakeWindowDao(),
            hourDao = FakeHourSummaryDao(),
            openThreadDao = FakeOpenThreadDao(),
            ownerDao = FakeOwnerDao(),
            anonymizer = Anonymizer(),
            interventionDao = com.pca.assistant.testing.FakeInterventionDao(),
        )
    }

    private suspend fun build(): LlmRequest {
        val slice = aggregator.collectSlice(0, 1000)
        return aggregator.buildRequest(slice, "en")
    }

    @Test fun `serialised payload contains a bucketed label not raw coords`() = runTest {
        transcriptDao.insert(
            TranscriptEntity(
                ts = 100, text = "I'm at the office now", textAnonymized = "I'm at the office now",
                speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                locationLat = 32.0853, locationLng = 34.7818,
                placeLabel = "home", confidence = 0.9f, language = "en",
            )
        )
        val req = build()
        val wire = json.encodeToString(LlmRequest.serializer(), req)
        assertFalse("raw lat must not be present", wire.contains("32.0853"))
        assertFalse("raw lng must not be present", wire.contains("34.7818"))
        assertNotNull(req.window.locationLabel)
    }

    @Test fun `phone numbers in transcripts are tokenised before the request is built`() = runTest {
        transcriptDao.insert(
            TranscriptEntity(
                ts = 100, text = "call me at +972 50-123-4567",
                textAnonymized = "call me at +972 50-123-4567",
                speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                locationLat = null, locationLng = null, placeLabel = "home",
                confidence = 0.9f, language = "en",
            )
        )
        val req = build()
        val wire = json.encodeToString(LlmRequest.serializer(), req)
        assertFalse(wire.contains("+972 50-123-4567"))
        assertTrue(wire.contains("[PHONE_"))
    }

    @Test fun `emails in transcripts are tokenised before the request is built`() = runTest {
        transcriptDao.insert(
            TranscriptEntity(
                ts = 100, text = "ping alice@example.com",
                textAnonymized = "ping alice@example.com",
                speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                locationLat = null, locationLng = null, placeLabel = "home",
                confidence = 0.9f, language = "en",
            )
        )
        val req = build()
        val wire = json.encodeToString(LlmRequest.serializer(), req)
        assertFalse(wire.contains("alice@example.com"))
        assertTrue(wire.contains("[EMAIL_"))
    }

    @Test fun `WindowPayload type does NOT carry raw coordinate fields`() {
        // Structural check — a refactor that adds lat/lng to the wire type
        // would silently break this guarantee. This test guards the type.
        val members = com.pca.assistant.llm.contract.WindowPayload::class.members.map { it.name }
        assertFalse("WindowPayload must not expose latitude/lat",
            members.any { it.equals("lat", true) || it.equals("latitude", true) })
        assertFalse("WindowPayload must not expose longitude/lng",
            members.any { it.equals("lng", true) || it.equals("longitude", true) })
    }

    @Test fun `system prompt does NOT carry an email (slipped owner identity guard)`() {
        // The prompt is shipped on every request — a stray '@' would imply
        // a hard-coded identity hint, which is a privacy red flag.
        assertFalse(SystemPrompt.EVALUATOR.contains("@"))
    }

    @Test fun `voice embedding bytes never appear in the LLM payload`() = runTest {
        // The owner table has a voiceEmbedding BLOB — make sure it isn't
        // accidentally serialised into the L3 profile or anywhere else.
        // We can't easily search for arbitrary bytes inside JSON; instead
        // we assert the request type has no embedding field at all.
        val req = build()
        val members = req::class.members.map { it.name.lowercase() }
        assertFalse(members.any { "embedding" in it })
        assertFalse(members.any { "voice" in it })
    }
}
