package com.pca.assistant.security

import com.pca.assistant.anonymizer.Anonymizer
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.pipeline.WindowAggregator
import com.pca.assistant.testing.FakeHourSummaryDao
import com.pca.assistant.testing.FakeOpenThreadDao
import com.pca.assistant.testing.FakeOwnerDao
import com.pca.assistant.testing.FakeTranscriptDao
import com.pca.assistant.testing.FakeWindowDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test for `PCA-S-1`: per-chunk anonymisation produced incoherent
 * tokens across chunks, so two different PII originals could end up sharing
 * the same `[EMAIL_1]` token in the LLM payload. The fix moved anonymisation
 * to [WindowAggregator.collectSlice]; this test pins the new invariant.
 */
class AnonymizerCollisionRegressionTest {

    private lateinit var transcriptDao: FakeTranscriptDao
    private lateinit var aggregator: WindowAggregator

    @Before fun setUp() {
        transcriptDao = FakeTranscriptDao()
        aggregator = WindowAggregator(
            transcriptDao = transcriptDao,
            windowDao = FakeWindowDao(),
            hourDao = FakeHourSummaryDao(),
            openThreadDao = FakeOpenThreadDao(),
            ownerDao = FakeOwnerDao(),
            anonymizer = Anonymizer(),
        )
    }

    private suspend fun seed(ts: Long, raw: String) {
        // Per-chunk `textAnonymized` is intentionally a copy of raw — the
        // pipeline re-anonymises at window-build time and that path is what
        // we're testing.
        transcriptDao.insert(
            TranscriptEntity(
                ts = ts, text = raw, textAnonymized = raw,
                speakerId = "owner", speakerScore = 0.9f, isOwner = true,
                locationLat = null, locationLng = null, placeLabel = "home",
                confidence = 0.9f, language = "en",
            )
        )
    }

    @Test fun `two emails across two chunks get DISTINCT tokens in the window payload`() = runTest {
        seed(100, "call alice@example.com about lunch")
        seed(200, "and also bob@example.com about dinner")

        val slice = aggregator.collectSlice(0, 1000)

        assertFalse("raw email must not leak", slice.transcriptAnonymized.contains("alice@example.com"))
        assertFalse("raw email must not leak", slice.transcriptAnonymized.contains("bob@example.com"))
        assertTrue("first email tokenised", slice.transcriptAnonymized.contains("[EMAIL_1]"))
        assertTrue(
            "second email MUST get a distinct token — otherwise the LLM sees [EMAIL_1] for two originals",
            slice.transcriptAnonymized.contains("[EMAIL_2]")
        )
    }

    @Test fun `the same email mentioned in two chunks reuses the same token`() = runTest {
        seed(100, "ping alice@example.com when you're free")
        seed(200, "if not alice@example.com let me know")

        val slice = aggregator.collectSlice(0, 1000)

        // Single original ⇒ single token, but should appear twice in the text.
        val countEmail1 = Regex("\\[EMAIL_1]").findAll(slice.transcriptAnonymized).count()
        assertEquals(2, countEmail1)
        assertFalse(slice.transcriptAnonymized.contains("[EMAIL_2]"))
    }

    @Test fun `phones across chunks are distinct tokens too`() = runTest {
        seed(100, "call +972 50-111-1111 first")
        seed(200, "then +972 50-222-2222 if no answer")

        val slice = aggregator.collectSlice(0, 1000)

        assertTrue(slice.transcriptAnonymized.contains("[PHONE_1]"))
        assertTrue(slice.transcriptAnonymized.contains("[PHONE_2]"))
        // Make sure the two PHONE tokens aren't the same string by accident.
        val parts = slice.transcriptAnonymized.split(" ").filter { it.contains("[PHONE_") }
        assertNotEquals("PHONE_1 and PHONE_2 must reference different positions", 1, parts.toSet().size)
    }

    @Test fun `mixed PII types across chunks stay coherent`() = runTest {
        seed(100, "email alice@example.com or call +44 20 7946 0958")
        seed(200, "or bob@example.com on +1 415 555 0100")

        val slice = aggregator.collectSlice(0, 1000)

        // 2 distinct emails + 2 distinct phones, all reachable in one window.
        assertTrue(slice.transcriptAnonymized.contains("[EMAIL_1]"))
        assertTrue(slice.transcriptAnonymized.contains("[EMAIL_2]"))
        assertTrue(slice.transcriptAnonymized.contains("[PHONE_1]"))
        assertTrue(slice.transcriptAnonymized.contains("[PHONE_2]"))
    }

    @Test fun `raw transcript is preserved verbatim alongside the anonymised one`() = runTest {
        seed(100, "alice@example.com sent +972 50-111-1111")

        val slice = aggregator.collectSlice(0, 1000)

        // Raw must keep the original for the owner-facing audit view…
        assertTrue(slice.transcriptOriginal.contains("alice@example.com"))
        assertTrue(slice.transcriptOriginal.contains("+972 50-111-1111"))
        // …but the anonymised form must not.
        assertFalse(slice.transcriptAnonymized.contains("alice@example.com"))
        assertFalse(slice.transcriptAnonymized.contains("+972 50-111-1111"))
    }
}
