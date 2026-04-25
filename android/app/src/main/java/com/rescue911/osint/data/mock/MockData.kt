package com.rescue911.osint.data.mock

import com.rescue911.osint.domain.model.AuditEntry
import com.rescue911.osint.domain.model.CaseStatus
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.EvidenceStatus
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.domain.model.L3Action
import com.rescue911.osint.domain.model.MissingCase
import com.rescue911.osint.domain.model.Person
import com.rescue911.osint.domain.model.RiskLevel
import com.rescue911.osint.domain.model.SourceType
import com.rescue911.osint.domain.model.ValidationLevel1
import com.rescue911.osint.domain.model.ValidationLevel2
import com.rescue911.osint.domain.model.ValidationLevel3
import com.rescue911.osint.domain.model.ValidationState

object MockData {
    val cases: List<MissingCase> = listOf(
        MissingCase(
            id = "case-1",
            title = "Anna L.",
            description = "Last seen near Tel Aviv promenade, blue jacket.",
            status = CaseStatus.INVESTIGATING,
            person = Person(
                id = "p-1",
                fullName = "Anna Lifshitz",
                nameVariants = listOf("אנה ליפשיץ", "Анна Лифшиц"),
                age = 27,
            ),
            lastSeenLocation = "Tel Aviv-Yafo",
            lastSeenLat = 32.0853,
            lastSeenLon = 34.7818,
            createdAtIso = "2026-04-22T09:14:00Z",
        ),
        MissingCase(
            id = "case-2",
            title = "Yossi B.",
            description = "Hiker missing in Galilee region.",
            status = CaseStatus.HUMAN_REVIEW,
            person = Person(
                id = "p-2",
                fullName = "Yossi Ben-David",
                nameVariants = listOf("יוסי בן דוד", "Йосси Бен-Давид"),
                age = 54,
            ),
            lastSeenLocation = "Mount Meron trail",
            lastSeenLat = 32.997,
            lastSeenLon = 35.408,
            createdAtIso = "2026-04-23T18:30:00Z",
        ),
        MissingCase(
            id = "case-3",
            title = "Dina K.",
            description = "Possible mental-health vulnerability; family searching.",
            status = CaseStatus.OPEN,
            person = Person(
                id = "p-3",
                fullName = "Dina Kohen",
                nameVariants = listOf("דינה כהן", "Дина Коэн"),
                age = 19,
            ),
            lastSeenLocation = "Haifa central bus station",
            lastSeenLat = 32.819,
            lastSeenLon = 34.997,
            createdAtIso = "2026-04-24T11:05:00Z",
        ),
    )

    private val l1Pass = ValidationLevel1(
        schemaValid = true,
        urlOrSourceValid = true,
        timestampValid = true,
        providerResponseValid = true,
        contentHashStored = true,
        duplicateCheckPassed = true,
        legalSource = true,
    )

    val evidence: List<Evidence> = listOf(
        Evidence(
            id = "ev-1",
            caseId = "case-1",
            sourceType = SourceType.SOCIAL,
            provider = "telegram_public",
            title = "Public sighting on volunteer channel",
            url = "https://t.me/example/123",
            snippet = "Photo with timestamp matching last-seen window.",
            language = "he",
            confidence = 0.62,
            status = EvidenceStatus.NEEDS_REVIEW,
            validation = ValidationState(level1 = l1Pass, level2 = ValidationLevel2(independentSources = 1)),
            nextAction = "Cross-check with archive snapshot",
        ),
        Evidence(
            id = "ev-2",
            caseId = "case-1",
            sourceType = SourceType.GEOINT,
            provider = "geoseer",
            title = "Image-geo candidate near promenade",
            snippet = "Coastline match, low confidence.",
            confidence = 0.35,
            status = EvidenceStatus.CANDIDATE,
            riskFlags = listOf("low_confidence"),
            validation = ValidationState(level1 = l1Pass),
            nextAction = "Validate POI on maps provider",
        ),
        Evidence(
            id = "ev-3",
            caseId = "case-2",
            sourceType = SourceType.ARCHIVE,
            provider = "wayback_cdx",
            title = "Public hiking forum thread (archived)",
            url = "https://web.archive.org/web/...",
            snippet = "User mentioned planning Meron route.",
            language = "ru",
            confidence = 0.45,
            status = EvidenceStatus.CORROBORATED,
            riskFlags = listOf("archive_only"),
            validation = ValidationState(
                level1 = l1Pass,
                level2 = ValidationLevel2(independentSources = 2, isPassed = true),
                level3 = ValidationLevel3(action = L3Action.PENDING),
            ),
            nextAction = "Operator confirm",
        ),
    )

    val hypotheses: List<Hypothesis> = listOf(
        Hypothesis(
            id = "h-1",
            caseId = "case-1",
            label = "Tel Aviv-Yafo promenade",
            lat = 32.0853, lon = 34.7818,
            placeName = "Tel Aviv-Yafo",
            confidence = 0.55,
            evidenceIds = listOf("ev-1", "ev-2"),
            contradictions = listOf("EXIF timestamp drift: ±90min"),
            risk = RiskLevel.MEDIUM,
            nextChecks = listOf("Street-view confirm", "Second sighting"),
        ),
        Hypothesis(
            id = "h-2",
            caseId = "case-2",
            label = "Mount Meron trail north fork",
            lat = 32.997, lon = 35.408,
            placeName = "Mount Meron",
            confidence = 0.42,
            evidenceIds = listOf("ev-3"),
            risk = RiskLevel.HIGH,
            nextChecks = listOf("Trail camera images", "Park ranger contact"),
        ),
    )

    val auditLog: List<AuditEntry> = listOf(
        AuditEntry(id = "a-1", action = "case.create", actorId = "ops:dmitriy", targetType = "case", targetId = "case-1", createdAtIso = "2026-04-22T09:14:00Z"),
        AuditEntry(id = "a-2", action = "provider_call", actorId = null, targetType = "social_search", targetId = null, createdAtIso = "2026-04-22T09:18:00Z"),
        AuditEntry(id = "a-3", action = "review.confirm", actorId = "ops:reviewer", targetType = "evidence", targetId = "ev-3", createdAtIso = "2026-04-23T19:00:00Z"),
    )
}
