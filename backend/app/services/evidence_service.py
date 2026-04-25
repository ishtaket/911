"""Evidence normalization — dedup by content hash; L1 validation."""
from __future__ import annotations

from uuid import UUID

from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.provider_result import ProviderResult, SourceType
from app.schemas.validation import ValidationLevel1, ValidationState
from app.services.store import get_store


def _level1(pr: ProviderResult) -> ValidationLevel1:
    """Run automated L1 checks against a provider result."""
    return ValidationLevel1(
        schema_valid=True,  # Pydantic validated already
        url_or_source_valid=bool(pr.url) or bool(pr.snippet) or bool(pr.title),
        timestamp_valid=pr.fetched_at is not None,
        provider_response_valid=True,
        content_hash_stored=bool(pr.content_hash),
        duplicate_check_passed=_dedup_check(pr),
        legal_source=pr.is_legal_source,
    )


def _dedup_check(pr: ProviderResult) -> bool:
    if not pr.content_hash:
        return True
    store = get_store()
    return not any(e.content_hash == pr.content_hash for e in store.evidence.values())


def normalize_and_store(case_id: UUID, results: list[ProviderResult]) -> list[Evidence]:
    out: list[Evidence] = []
    store = get_store()
    for pr in results:
        l1 = _level1(pr)
        if not l1.duplicate_check_passed:
            continue  # silently dedupe
        evidence = Evidence(
            case_id=case_id,
            source_type=pr.source_type,
            provider=pr.provider,
            title=pr.title,
            url=pr.url,
            snippet=pr.snippet,
            language=pr.language,
            confidence=pr.confidence,
            risk_flags=list(pr.risk_flags),
            content_hash=pr.content_hash,
            status=EvidenceStatus.CANDIDATE,
            validation=ValidationState(level1=l1),
            next_action=_next_action_for(pr.source_type),
        )
        out.append(store.add_evidence(evidence))
    return out


def _next_action_for(source_type: SourceType) -> str:
    return {
        SourceType.WEB: "Cross-check with archive snapshot",
        SourceType.SOCIAL: "Verify account is public, request L2 corroboration",
        SourceType.ARCHIVE: "Verify against live source if reachable",
        SourceType.GEOINT: "Validate POI on maps provider",
        SourceType.MAPS: "Confirm with second maps source",
        SourceType.OCR: "Translate detected text and cross-check",
        SourceType.VISION: "Run reasoner with other-provider context",
        SourceType.EXIF: "Compare with other media in the case",
        SourceType.OFFICIAL: "Escalate to manual review",
    }.get(source_type, "Operator review")
