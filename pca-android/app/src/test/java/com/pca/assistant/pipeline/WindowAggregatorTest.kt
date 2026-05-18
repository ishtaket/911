package com.pca.assistant.pipeline

import com.pca.assistant.data.db.entity.HourSummaryEntity
import com.pca.assistant.data.db.entity.OpenThreadEntity
import com.pca.assistant.data.db.entity.OwnerProfileEntity
import com.pca.assistant.data.db.entity.TranscriptEntity
import com.pca.assistant.testing.FakeHourSummaryDao
import com.pca.assistant.testing.FakeOpenThreadDao
import com.pca.assistant.testing.FakeOwnerDao
import com.pca.assistant.testing.FakeTranscriptDao
import com.pca.assistant.testing.FakeWindowDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WindowAggregatorTest {

    private lateinit var ownerDao: FakeOwnerDao
    private lateinit var transcriptDao: FakeTranscriptDao
    private lateinit var windowDao: FakeWindowDao
    private lateinit var hourDao: FakeHourSummaryDao
    private lateinit var openThreadDao: FakeOpenThreadDao
    private lateinit var agg: WindowAggregator

    @Before fun setUp() {
        ownerDao = FakeOwnerDao()
        transcriptDao = FakeTranscriptDao()
        windowDao = FakeWindowDao()
        hourDao = FakeHourSummaryDao()
        openThreadDao = FakeOpenThreadDao()
        agg = WindowAggregator(transcriptDao, windowDao, hourDao, openThreadDao, ownerDao)
    }

    @Test fun `collectSlice over an empty range yields hadSpeech=false`() = runTest {
        val slice = agg.collectSlice(from = 0, to = 1000)
        assertEquals(0, slice.transcriptOriginal.length)
        assertFalse(slice.hadSpeech)
        assertFalse(slice.isOwnerPresent)
    }

    @Test fun `collectSlice stitches transcripts in [from to) range`() = runTest {
        transcriptDao.insert(transcript(ts = 100, text = "hello", anon = "hello", isOwner = true, place = "home"))
        transcriptDao.insert(transcript(ts = 200, text = "world", anon = "world", isOwner = false, place = "home"))
        // Outside range — must be excluded.
        transcriptDao.insert(transcript(ts = 999_999, text = "future", anon = "future", isOwner = true))

        val slice = agg.collectSlice(from = 0, to = 500)
        assertEquals("hello world", slice.transcriptOriginal)
        assertEquals("hello world", slice.transcriptAnonymized)
        assertTrue(slice.hadSpeech)
        assertTrue("owner was present in one of the rows", slice.isOwnerPresent)
        assertEquals("home", slice.locationLabel)
    }

    @Test fun `collectSlice picks the most recent location label when rows differ`() = runTest {
        transcriptDao.insert(transcript(ts = 100, place = "home"))
        transcriptDao.insert(transcript(ts = 200, place = "work"))
        transcriptDao.insert(transcript(ts = 300, place = null))
        val slice = agg.collectSlice(from = 0, to = 1000)
        assertEquals("work", slice.locationLabel)
    }

    @Test fun `buildRequest fills L1 from the most recent hour summaries`() = runTest {
        val now = 10_000_000L
        hourDao.upsert(HourSummaryEntity(
            hourStart = now - 30 * 60_000L, hourEnd = now - 30 * 60_000L + 60_000L,
            summary = "talk about lunch",
            memoryNotesJson = "[]", locationsJson = "[]", peopleJson = "[]",
        ))
        // An old summary (more than 60 min ago) — must not appear in L1.
        hourDao.upsert(HourSummaryEntity(
            hourStart = now - 5 * 60 * 60_000L, hourEnd = now - 5 * 60 * 60_000L + 60_000L,
            summary = "ancient history",
            memoryNotesJson = "[]", locationsJson = "[]", peopleJson = "[]",
        ))
        val slice = agg.collectSlice(0, now)
        val req = agg.buildRequest(slice.copy(endTs = now), "en")
        assertTrue("L1 must include the recent summary, got: ${req.l1Hour}", req.l1Hour.contains("lunch"))
        assertFalse("L1 must not include the older summary", req.l1Hour.contains("ancient"))
    }

    @Test fun `buildRequest falls back to placeholder when no owner profile`() = runTest {
        val slice = agg.collectSlice(0, 1000)
        val req = agg.buildRequest(slice, "en")
        assertTrue(req.l3Profile.contains("no profile"))
    }

    @Test fun `buildRequest includes owner profile when present`() = runTest {
        ownerDao.upsert(OwnerProfileEntity(
            id = 1, name = "Owner", voiceEmbedding = null,
            preferencesJson = "{}", l3Summary = "loves cycling",
            preferredLanguage = "en", updatedAt = 1,
        ))
        val req = agg.buildRequest(agg.collectSlice(0, 1000), "en")
        assertEquals("loves cycling", req.l3Profile)
    }

    @Test fun `buildRequest carries open threads through to the wire payload`() = runTest {
        openThreadDao.upsert(OpenThreadEntity(
            id = "t1", topic = "dentist", openedAt = 0, openedInWindowId = 1,
            context = "tomorrow", due = null, status = "open",
            lastMentionedAt = 0, relatedPeopleJson = "[]", relatedLocationsJson = "[]",
        ))
        // A closed thread must be filtered out.
        openThreadDao.upsert(OpenThreadEntity(
            id = "t2", topic = "milk", openedAt = 0, openedInWindowId = 1,
            context = "", due = null, status = "closed",
            lastMentionedAt = 0, relatedPeopleJson = "[]", relatedLocationsJson = "[]",
        ))
        val req = agg.buildRequest(agg.collectSlice(0, 1000), "en")
        assertEquals(1, req.openThreads.size)
        assertEquals("dentist", req.openThreads.first().topic)
    }

    @Test fun `persistWindow records anonymized + raw separately for audit`() = runTest {
        val slice = WindowAggregator.WindowSlice(
            windowId = 0, startTs = 1, endTs = 2,
            transcriptOriginal = "Call alice@example.com",
            transcriptAnonymized = "Call [EMAIL_1]",
            isOwnerPresent = true, locationLabel = "home", hadSpeech = true,
        )
        val id = agg.persistWindow(slice, providerId = "mock-local", sent = true, latencyMs = 12L)
        val saved = windowDao.byId(id)
        assertNotNull(saved)
        assertTrue("raw must contain the email", saved!!.rawContextJson.contains("alice@example.com"))
        assertFalse("anonymized must NOT contain the email", saved.anonymizedContextJson.contains("alice@example.com"))
        assertTrue(saved.anonymizedContextJson.contains("[EMAIL_1]"))
        assertEquals("mock-local", saved.llmProvider)
        assertEquals(12L, saved.latencyMs)
    }

    @Test fun `persistWindow on an empty slice marks it skipped with reason`() = runTest {
        val slice = WindowAggregator.WindowSlice(
            windowId = 0, startTs = 1, endTs = 2,
            transcriptOriginal = "", transcriptAnonymized = "",
            isOwnerPresent = false, locationLabel = null, hadSpeech = false,
        )
        val id = agg.persistWindow(slice, providerId = null, sent = false, latencyMs = null)
        val saved = windowDao.byId(id)!!
        assertTrue(saved.skipped)
        assertEquals("no_speech", saved.skipReason)
        assertNull(saved.llmProvider)
    }

    private fun transcript(
        ts: Long,
        text: String = "hello",
        anon: String = text,
        isOwner: Boolean = false,
        place: String? = null,
    ) = TranscriptEntity(
        ts = ts, text = text, textAnonymized = anon,
        speakerId = if (isOwner) "owner" else "other",
        speakerScore = if (isOwner) 0.9f else 0.1f,
        isOwner = isOwner, locationLat = null, locationLng = null,
        placeLabel = place, confidence = 0.5f, language = "en",
    )
}
