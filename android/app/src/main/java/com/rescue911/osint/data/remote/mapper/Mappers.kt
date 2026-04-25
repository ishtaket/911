package com.rescue911.osint.data.remote.mapper

import com.rescue911.osint.data.remote.dto.AuditEntryDto
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

fun CaseStatusDto.toDomain(): CaseStatus = when (this) {
    CaseStatusDto.OPEN -> CaseStatus.OPEN
    CaseStatusDto.INVESTIGATING -> CaseStatus.INVESTIGATING
    CaseStatusDto.HUMAN_REVIEW -> CaseStatus.HUMAN_REVIEW
    CaseStatusDto.RESOLVED -> CaseStatus.RESOLVED
    CaseStatusDto.CLOSED -> CaseStatus.CLOSED
}

fun EvidenceStatusDto.toDomain(): EvidenceStatus = when (this) {
    EvidenceStatusDto.CANDIDATE -> EvidenceStatus.CANDIDATE
    EvidenceStatusDto.NEEDS_REVIEW -> EvidenceStatus.NEEDS_REVIEW
    EvidenceStatusDto.CORROBORATED -> EvidenceStatus.CORROBORATED
    EvidenceStatusDto.REJECTED -> EvidenceStatus.REJECTED
    EvidenceStatusDto.HUMAN_CONFIRMED -> EvidenceStatus.HUMAN_CONFIRMED
}

fun RiskLevelDto.toDomain(): RiskLevel = when (this) {
    RiskLevelDto.LOW -> RiskLevel.LOW
    RiskLevelDto.MEDIUM -> RiskLevel.MEDIUM
    RiskLevelDto.HIGH -> RiskLevel.HIGH
    RiskLevelDto.CRITICAL -> RiskLevel.CRITICAL
}

fun SourceTypeDto.toDomain(): SourceType = when (this) {
    SourceTypeDto.WEB -> SourceType.WEB
    SourceTypeDto.SOCIAL -> SourceType.SOCIAL
    SourceTypeDto.ARCHIVE -> SourceType.ARCHIVE
    SourceTypeDto.GEOINT -> SourceType.GEOINT
    SourceTypeDto.MAPS -> SourceType.MAPS
    SourceTypeDto.OCR -> SourceType.OCR
    SourceTypeDto.VISION -> SourceType.VISION
    SourceTypeDto.EXIF -> SourceType.EXIF
    SourceTypeDto.OFFICIAL -> SourceType.OFFICIAL
}

fun L3ActionDto.toDomain(): L3Action = when (this) {
    L3ActionDto.PENDING -> L3Action.PENDING
    L3ActionDto.CONFIRM -> L3Action.CONFIRM
    L3ActionDto.REJECT -> L3Action.REJECT
    L3ActionDto.NEEDS_MORE_CHECKS -> L3Action.NEEDS_MORE_CHECKS
    L3ActionDto.ESCALATE_TO_AUTHORITIES -> L3Action.ESCALATE_TO_AUTHORITIES
    L3ActionDto.CONTACT_MANUALLY -> L3Action.CONTACT_MANUALLY
}

fun ValidationLevel1Dto.toDomain(): ValidationLevel1 = ValidationLevel1(
    schemaValid = schemaValid,
    urlOrSourceValid = urlOrSourceValid,
    timestampValid = timestampValid,
    providerResponseValid = providerResponseValid,
    contentHashStored = contentHashStored,
    duplicateCheckPassed = duplicateCheckPassed,
    legalSource = legalSource,
)

fun ValidationLevel2Dto.toDomain(): ValidationLevel2 = ValidationLevel2(
    independentSources = independentSources,
    crossSignals = crossSignals,
    isPassed = isPassed,
)

fun ValidationLevel3Dto.toDomain(): ValidationLevel3 = ValidationLevel3(
    action = action.toDomain(),
    reviewerId = reviewerId,
    reviewedAtIso = reviewedAt,
    note = note,
)

fun ValidationStateDto.toDomain(): ValidationState = ValidationState(
    level1 = level1.toDomain(),
    level2 = level2.toDomain(),
    level3 = level3.toDomain(),
)

fun PersonDto.toDomain(): Person = Person(
    id = id,
    fullName = fullName,
    nameVariants = nameVariants,
    age = age,
    description = description,
    photoUrls = photoUrls,
    socialHandles = socialHandles,
)

fun CaseDto.toDomain(): MissingCase = MissingCase(
    id = id,
    title = title,
    description = description,
    status = status.toDomain(),
    person = person.toDomain(),
    lastSeenLocation = lastSeenLocation,
    lastSeenLat = lastSeenLat,
    lastSeenLon = lastSeenLon,
    languages = languages,
    createdAtIso = createdAt,
)

fun EvidenceDto.toDomain(): Evidence = Evidence(
    id = id,
    caseId = caseId,
    sourceType = sourceType.toDomain(),
    provider = provider,
    title = title,
    url = url,
    snippet = snippet,
    language = language,
    confidence = confidence,
    riskFlags = riskFlags,
    status = status.toDomain(),
    validation = validation.toDomain(),
    nextAction = nextAction,
)

fun HypothesisDto.toDomain(): Hypothesis = Hypothesis(
    id = id,
    caseId = caseId,
    label = label,
    lat = lat,
    lon = lon,
    placeName = placeName,
    confidence = confidence,
    evidenceIds = evidenceIds,
    contradictions = contradictions,
    risk = risk.toDomain(),
    nextChecks = nextChecks,
)

fun AuditEntryDto.toDomain(): AuditEntry = AuditEntry(
    id = id,
    action = action,
    actorId = actorId,
    targetType = targetType,
    targetId = targetId,
    createdAtIso = createdAt,
)
