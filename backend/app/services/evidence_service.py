"""Evidence normalization — dedup by content hash; L1 validation."""
from __future__ import annotations

from uuid import UUID

from app.config import get_settings
from app.schemas.evidence import Evidence, EvidenceStatus
from app.schemas.provider_result import ProviderResult, SourceType
from app.schemas.validation import ValidationLevel1, ValidationState
from app.services.store import get_store


def _level1(case_id: UUID, pr: ProviderResult) -> ValidationLevel1:
    """Run automated L1 checks against a provider result."""
    return ValidationLevel1(
        schema_valid=True,  # Pydantic validated already
        url_or_source_valid=bool(pr.url) or bool(pr.snippet) or bool(pr.title),
        timestamp_valid=pr.fetched_at is not None,
        provider_response_valid=True,
        content_hash_stored=bool(pr.content_hash),
        duplicate_check_passed=_dedup_check(case_id, pr),
        legal_source=pr.is_legal_source,
    )


def _dedup_check(case_id: UUID, pr: ProviderResult) -> bool:
    """Per-case content-hash dedup.

    Two cases that issue the same query against the same provider will
    legitimately see the same URL — that's not a "duplicate" from the
    operator's point of view, it's "this URL is relevant to my case
    too". Scope dedup to *this* case so each case has its own evidence
    inventory and isn't silently starved by an earlier case's results.
    """
    if not pr.content_hash:
        return True
    store = get_store()
    return not any(
        e.content_hash == pr.content_hash and e.case_id == case_id
        for e in store.evidence.values()
    )


def normalize_and_store_with_stats(
    case_id: UUID, results: list[ProviderResult]
) -> tuple[list[Evidence], int]:
    """Same as :func:`normalize_and_store`, but also returns how many
    incoming items were skipped because their content hash already exists
    in the store. Used by the per-channel orchestrator so the dispatch
    response can tell the operator *why* the call returned 0 — silent
    dedupe used to look identical to "no_results"."""
    out: list[Evidence] = []
    deduped = 0
    store = get_store()
    # Belt-and-suspenders: in strict Backend mode, refuse to store any
    # ProviderResult whose provider name starts with "mock_". The
    # registry already gates MockArchiveProvider/MockWebSearchProvider/
    # MockSocialSearchProvider behind MOCK_PROVIDERS=true, but this
    # guard prevents a future regression (e.g. a stub that silently
    # falls back to a mock) from leaking fake evidence into the
    # operator's case in real mode.
    strict_mode = not get_settings().mock_providers
    for pr in results:
        if strict_mode and (pr.provider or "").startswith("mock_"):
            continue
        l1 = _level1(case_id, pr)
        if not l1.duplicate_check_passed:
            deduped += 1
            continue
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
    return out, deduped


def normalize_and_store(case_id: UUID, results: list[ProviderResult]) -> list[Evidence]:
    stored, _ = normalize_and_store_with_stats(case_id, results)
    return stored


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
