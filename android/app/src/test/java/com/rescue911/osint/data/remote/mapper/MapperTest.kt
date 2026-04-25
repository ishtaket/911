package com.rescue911.osint.data.remote.mapper

import com.rescue911.osint.data.remote.dto.CaseDto
import com.rescue911.osint.data.remote.dto.CaseStatusDto
import com.rescue911.osint.data.remote.dto.EvidenceDto
import com.rescue911.osint.data.remote.dto.EvidenceStatusDto
import com.rescue911.osint.data.remote.dto.HypothesisDto
import com.rescue911.osint.data.remote.dto.L3ActionDto
import com.rescue911.osint.data.remote.dto.PersonDto
import com.rescue911.osint.data.remote.dto.RiskLevelDto
import com.rescue911.osint.data.remote.dto.SourceTypeDto
import com.rescue911.osint.data.remote.dto.ValidationLevel1Dto
import com.rescue911.osint.data.remote.dto.ValidationLevel2Dto
import com.rescue911.osint.data.remote.dto.ValidationLevel3Dto
import com.rescue911.osint.data.remote.dto.ValidationStateDto
import com.rescue911.osint.domain.model.CaseStatus
import com.rescue911.osint.domain.model.EvidenceStatus
import com.rescue911.osint.domain.model.L3Action
import com.rescue911.osint.domain.model.RiskLevel
import com.rescue911.osint.domain.model.SourceType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `case dto round-trips snake_case json`() {
        val raw = """
            {
              "id":"11111111-1111-4111-8111-111111111111",
              "title":"Anna L.",
              "description":"Last seen near promenade",
              "status":"investigating",
              "person":{"id":"p1","full_name":"Anna Lifshitz","name_variants":["אנה"],"age":27},
              "last_seen_location":"Tel Aviv-Yafo",
              "last_seen_lat":32.0853,
              "last_seen_lon":34.7818,
              "languages":["en","he","ru"],
              "created_at":"2026-04-25T10:00:00Z"
            }
        """.trimIndent()
        val dto = json.decodeFromString(CaseDto.serializer(), raw)
        val domain = dto.toDomain()
        assertEquals("Anna L.", domain.title)
        assertEquals(CaseStatus.INVESTIGATING, domain.status)
        assertEquals("Anna Lifshitz", domain.person.fullName)
        assertEquals(listOf("אנה"), domain.person.nameVariants)
        assertEquals("Tel Aviv-Yafo", domain.lastSeenLocation)
        assertEquals(32.0853, domain.lastSeenLat!!, 1e-6)
        assertEquals("2026-04-25T10:00:00Z", domain.createdAtIso)
    }

    @Test
    fun `evidence dto preserves validation tri-level state`() {
        val raw = """
            {
              "id":"e1","case_id":"c1","source_type":"social","provider":"telegram_public",
              "title":"sighting","confidence":0.6,"risk_flags":["low_confidence"],
              "status":"needs_review",
              "validation":{
                "level1":{"schema_valid":true,"url_or_source_valid":true,"timestamp_valid":true,
                          "provider_response_valid":true,"content_hash_stored":true,
                          "duplicate_check_passed":true,"legal_source":true},
                "level2":{"independent_sources":1,"cross_signals":["timestamp_match"],"is_passed":false},
                "level3":{"action":"pending"}
              },
              "next_action":"Cross-check"
            }
        """.trimIndent()
        val dto = json.decodeFromString(EvidenceDto.serializer(), raw)
        val d = dto.toDomain()
        assertEquals(SourceType.SOCIAL, d.sourceType)
        assertEquals(EvidenceStatus.NEEDS_REVIEW, d.status)
        assertTrue(d.validation.level1.isPassed)
        assertEquals(1, d.validation.level2.independentSources)
        assertEquals(L3Action.PENDING, d.validation.level3.action)
        assertEquals(listOf("low_confidence"), d.riskFlags)
    }

    @Test
    fun `hypothesis dto maps risk and place fields`() {
        val raw = """
            {
              "id":"h1","case_id":"c1","label":"Promenade",
              "lat":32.08,"lon":34.78,"place_name":"Tel Aviv-Yafo",
              "confidence":0.5,"evidence_ids":["e1","e2"],"contradictions":["EXIF drift"],
              "risk":"high","next_checks":["Street view"]
            }
        """.trimIndent()
        val dto = json.decodeFromString(HypothesisDto.serializer(), raw)
        val d = dto.toDomain()
        assertEquals("Promenade", d.label)
        assertEquals("Tel Aviv-Yafo", d.placeName)
        assertEquals(RiskLevel.HIGH, d.risk)
        assertEquals(listOf("e1", "e2"), d.evidenceIds)
        assertEquals(listOf("EXIF drift"), d.contradictions)
    }

    @Test
    fun `enum names round-trip lowercase strings`() {
        // Backend serialises enums as lowercase snake_case; ensure each enum
        // can decode and map cleanly.
        val statuses = listOf(
            "open" to CaseStatus.OPEN,
            "investigating" to CaseStatus.INVESTIGATING,
            "human_review" to CaseStatus.HUMAN_REVIEW,
            "resolved" to CaseStatus.RESOLVED,
            "closed" to CaseStatus.CLOSED,
        )
        statuses.forEach { (raw, expected) ->
            val dto = json.decodeFromString(CaseStatusDto.serializer(), "\"$raw\"")
            assertEquals(expected, dto.toDomain())
        }

        val l3 = listOf(
            "pending" to L3Action.PENDING,
            "confirm" to L3Action.CONFIRM,
            "reject" to L3Action.REJECT,
            "needs_more_checks" to L3Action.NEEDS_MORE_CHECKS,
            "escalate_to_authorities" to L3Action.ESCALATE_TO_AUTHORITIES,
            "contact_manually" to L3Action.CONTACT_MANUALLY,
        )
        l3.forEach { (raw, expected) ->
            val dto = json.decodeFromString(L3ActionDto.serializer(), "\"$raw\"")
            assertEquals(expected, dto.toDomain())
        }
    }

    @Test
    fun `person dto handles minimal payload`() {
        // Backend may omit optional fields; defaults must work.
        val raw = """{"id":"p","full_name":"X"}"""
        val dto = json.decodeFromString(PersonDto.serializer(), raw)
        val d = dto.toDomain()
        assertEquals("X", d.fullName)
        assertTrue(d.nameVariants.isEmpty())
        assertTrue(d.photoUrls.isEmpty())
    }

    @Test
    fun `validation state defaults are all-false but well-formed`() {
        val v = ValidationStateDto(
            level1 = ValidationLevel1Dto(),
            level2 = ValidationLevel2Dto(),
            level3 = ValidationLevel3Dto(),
        ).toDomain()
        assertTrue(!v.level1.isPassed)
        assertTrue(!v.level2.isPassed)
        assertEquals(L3Action.PENDING, v.level3.action)
    }
}
