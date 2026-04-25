package com.rescue911.osint.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ----- enums (lowercase strings on the wire) -----

@Serializable
enum class CaseStatusDto {
    @SerialName("open") OPEN,
    @SerialName("investigating") INVESTIGATING,
    @SerialName("human_review") HUMAN_REVIEW,
    @SerialName("resolved") RESOLVED,
    @SerialName("closed") CLOSED,
}

@Serializable
enum class EvidenceStatusDto {
    @SerialName("candidate") CANDIDATE,
    @SerialName("needs_review") NEEDS_REVIEW,
    @SerialName("corroborated") CORROBORATED,
    @SerialName("rejected") REJECTED,
    @SerialName("human_confirmed") HUMAN_CONFIRMED,
}

@Serializable
enum class RiskLevelDto {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL,
}

@Serializable
enum class SourceTypeDto {
    @SerialName("web") WEB,
    @SerialName("social") SOCIAL,
    @SerialName("archive") ARCHIVE,
    @SerialName("geoint") GEOINT,
    @SerialName("maps") MAPS,
    @SerialName("ocr") OCR,
    @SerialName("vision") VISION,
    @SerialName("exif") EXIF,
    @SerialName("official") OFFICIAL,
}

@Serializable
enum class L3ActionDto {
    @SerialName("pending") PENDING,
    @SerialName("confirm") CONFIRM,
    @SerialName("reject") REJECT,
    @SerialName("needs_more_checks") NEEDS_MORE_CHECKS,
    @SerialName("escalate_to_authorities") ESCALATE_TO_AUTHORITIES,
    @SerialName("contact_manually") CONTACT_MANUALLY,
}

// ----- validation -----

@Serializable
data class ValidationLevel1Dto(
    @SerialName("schema_valid") val schemaValid: Boolean = false,
    @SerialName("url_or_source_valid") val urlOrSourceValid: Boolean = false,
    @SerialName("timestamp_valid") val timestampValid: Boolean = false,
    @SerialName("provider_response_valid") val providerResponseValid: Boolean = false,
    @SerialName("content_hash_stored") val contentHashStored: Boolean = false,
    @SerialName("duplicate_check_passed") val duplicateCheckPassed: Boolean = false,
    @SerialName("legal_source") val legalSource: Boolean = false,
)

@Serializable
data class ValidationLevel2Dto(
    @SerialName("independent_sources") val independentSources: Int = 0,
    @SerialName("cross_signals") val crossSignals: List<String> = emptyList(),
    @SerialName("is_passed") val isPassed: Boolean = false,
)

@Serializable
data class ValidationLevel3Dto(
    val action: L3ActionDto = L3ActionDto.PENDING,
    @SerialName("reviewer_id") val reviewerId: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    val note: String? = null,
)

@Serializable
data class ValidationStateDto(
    val level1: ValidationLevel1Dto = ValidationLevel1Dto(),
    val level2: ValidationLevel2Dto = ValidationLevel2Dto(),
    val level3: ValidationLevel3Dto = ValidationLevel3Dto(),
)

// ----- domain payloads -----

@Serializable
data class PersonDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("name_variants") val nameVariants: List<String> = emptyList(),
    val age: Int? = null,
    val description: String? = null,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("social_handles") val socialHandles: List<String> = emptyList(),
)

@Serializable
data class CaseDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: CaseStatusDto = CaseStatusDto.OPEN,
    val person: PersonDto,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("last_seen_location") val lastSeenLocation: String? = null,
    @SerialName("last_seen_lat") val lastSeenLat: Double? = null,
    @SerialName("last_seen_lon") val lastSeenLon: Double? = null,
    val languages: List<String> = listOf("en", "he", "ru"),
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class EvidenceDto(
    val id: String,
    @SerialName("case_id") val caseId: String,
    @SerialName("source_type") val sourceType: SourceTypeDto,
    val provider: String,
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val language: String? = null,
    val confidence: Double = 0.0,
    @SerialName("risk_flags") val riskFlags: List<String> = emptyList(),
    val status: EvidenceStatusDto = EvidenceStatusDto.CANDIDATE,
    val validation: ValidationStateDto = ValidationStateDto(),
    @SerialName("next_action") val nextAction: String? = null,
)

@Serializable
data class HypothesisDto(
    val id: String,
    @SerialName("case_id") val caseId: String,
    val label: String,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("place_name") val placeName: String? = null,
    val confidence: Double = 0.0,
    @SerialName("evidence_ids") val evidenceIds: List<String> = emptyList(),
    val contradictions: List<String> = emptyList(),
    val risk: RiskLevelDto = RiskLevelDto.MEDIUM,
    @SerialName("next_checks") val nextChecks: List<String> = emptyList(),
)

@Serializable
data class AuditEntryDto(
    val id: String,
    val action: String,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("target_type") val targetType: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("created_at") val createdAt: String,
)

// ----- non-domain payloads (health, providers) -----

@Serializable
data class HealthDto(
    val status: String,
    val env: String? = null,
    val region: String? = null,
    val languages: List<String> = emptyList(),
    @SerialName("mock_providers") val mockProviders: Boolean = true,
)

@Serializable
enum class ProviderModeDto {
    @SerialName("live") LIVE,
    @SerialName("mock") MOCK,
    @SerialName("ready") READY,
    @SerialName("stub") STUB,
}

@Serializable
data class ProviderInfoDto(
    val name: String,
    val category: String,
    val mode: ProviderModeDto,
    val note: String? = null,
)

@Serializable
data class ProviderStatusResponseDto(
    @SerialName("mock_providers") val mockProviders: Boolean,
    val providers: List<ProviderInfoDto> = emptyList(),
)
