package com.rescue911.osint.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CaseStatus { OPEN, INVESTIGATING, HUMAN_REVIEW, RESOLVED, CLOSED }

@Serializable
enum class EvidenceStatus { CANDIDATE, NEEDS_REVIEW, CORROBORATED, REJECTED, HUMAN_CONFIRMED }

@Serializable
enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
enum class SourceType { WEB, SOCIAL, ARCHIVE, GEOINT, MAPS, OCR, VISION, EXIF, OFFICIAL }

@Serializable
enum class L3Action { PENDING, CONFIRM, REJECT, NEEDS_MORE_CHECKS, ESCALATE_TO_AUTHORITIES, CONTACT_MANUALLY }

@Serializable
data class Person(
    val id: String,
    val fullName: String,
    val nameVariants: List<String> = emptyList(),
    val age: Int? = null,
    val description: String? = null,
    val photoUrls: List<String> = emptyList(),
    val socialHandles: List<String> = emptyList(),
)

@Serializable
data class MissingCase(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: CaseStatus = CaseStatus.OPEN,
    val person: Person,
    val lastSeenLocation: String? = null,
    val lastSeenLat: Double? = null,
    val lastSeenLon: Double? = null,
    val languages: List<String> = listOf("en", "he", "ru"),
    val createdAtIso: String,
)

@Serializable
data class ValidationLevel1(
    val schemaValid: Boolean = false,
    val urlOrSourceValid: Boolean = false,
    val timestampValid: Boolean = false,
    val providerResponseValid: Boolean = false,
    val contentHashStored: Boolean = false,
    val duplicateCheckPassed: Boolean = false,
    val legalSource: Boolean = false,
) {
    val isPassed: Boolean
        get() = schemaValid && urlOrSourceValid && timestampValid &&
            providerResponseValid && contentHashStored && duplicateCheckPassed && legalSource
}

@Serializable
data class ValidationLevel2(
    val independentSources: Int = 0,
    val crossSignals: List<String> = emptyList(),
    val isPassed: Boolean = false,
)

@Serializable
data class ValidationLevel3(
    val action: L3Action = L3Action.PENDING,
    val reviewerId: String? = null,
    val reviewedAtIso: String? = null,
    val note: String? = null,
)

@Serializable
data class ValidationState(
    val level1: ValidationLevel1 = ValidationLevel1(),
    val level2: ValidationLevel2 = ValidationLevel2(),
    val level3: ValidationLevel3 = ValidationLevel3(),
) {
    val isCorroborated: Boolean get() = level1.isPassed && level2.isPassed
    val isHumanConfirmed: Boolean get() = level3.action == L3Action.CONFIRM
}

@Serializable
data class Evidence(
    val id: String,
    val caseId: String,
    val sourceType: SourceType,
    val provider: String,
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val language: String? = null,
    val confidence: Double = 0.0,
    val riskFlags: List<String> = emptyList(),
    val status: EvidenceStatus = EvidenceStatus.CANDIDATE,
    val validation: ValidationState = ValidationState(),
    val nextAction: String? = null,
)

@Serializable
data class Hypothesis(
    val id: String,
    val caseId: String,
    val label: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String? = null,
    val confidence: Double = 0.0,
    val evidenceIds: List<String> = emptyList(),
    val contradictions: List<String> = emptyList(),
    val risk: RiskLevel = RiskLevel.MEDIUM,
    val nextChecks: List<String> = emptyList(),
)

@Serializable
data class AuditEntry(
    val id: String,
    val action: String,
    val actorId: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val createdAtIso: String,
)
