"""Per-channel search orchestrator.

Wraps `search_service` (which today fans out to all channels) so callers
can dispatch only one channel at a time. The plumbing reaches the same
provider registry, evidence normalizer, and audit logger.
"""
from __future__ import annotations

from pydantic import BaseModel

from app.providers.registry import (
    get_archive_providers,
    get_social_search_providers,
    get_web_search_providers,
)
from app.schemas.audit import AuditEntryCreate
from app.schemas.case import Case
from app.schemas.evidence import Evidence
from app.schemas.provider_result import ProviderResult
from app.services import audit_service, evidence_service
from app.services.archive_target_builder import build_targets
from app.services.query_builder import build_query_plan
from app.services.search_service import _search_social, _search_web
from app.services.store import get_store


class ArchiveStartResponse(BaseModel):
    """Per-case archive dispatch result.

    `state` is one of:
      - "no_targets"  : case has no URLs yet — operator should run web/social
      - "no_results"  : targets attempted but providers returned nothing
      - "completed"   : at least one stored evidence item
    """
    state: str
    case_id: str
    targets_attempted: int
    message: str
    evidence: list[Evidence] = []


async def run_web(case: Case) -> list[Evidence]:
    plan = build_query_plan(case)
    raw = await _search_web(plan, get_web_search_providers())
    return evidence_service.normalize_and_store(case.id, raw)


async def run_social(case: Case) -> list[Evidence]:
    plan = build_query_plan(case)
    raw = await _search_social(plan, get_social_search_providers())
    return evidence_service.normalize_and_store(case.id, raw)


async def run_archive(case: Case) -> ArchiveStartResponse:
    """Strict archive dispatch: queries are anchored to URLs that already
    exist in the case's evidence (P1, P2) and host-wildcards derived from
    them (P3). No static domain list, no free-text fan-out."""
    case_evidence = get_store().list_evidence(case_id=case.id)
    targets = build_targets(case_evidence)

    if not targets:
        audit_service.log(
            AuditEntryCreate(
                action="archive_no_targets",
                target_type="archive",
                target_id=str(case.id),
                metadata={"reason": "no anchor URLs in case evidence"},
            )
        )
        return ArchiveStartResponse(
            state="no_targets",
            case_id=str(case.id),
            targets_attempted=0,
            message="No URLs in this case yet. Run a web or social search "
                    "first; archive providers will then have public URL "
                    "anchors to query.",
            evidence=[],
        )

    providers = get_archive_providers()
    raw_results: list[ProviderResult] = []
    for t in targets:
        for p in providers:
            audit_service.log(
                AuditEntryCreate(
                    action="provider_call",
                    target_type="archive",
                    metadata={
                        "provider": p.name,
                        "target": t.url_or_pattern,
                        "priority": t.priority,
                    },
                )
            )
            try:
                raw_results.extend(await p.lookup(t.url_or_pattern, limit=5))
            except Exception as exc:  # noqa: BLE001
                audit_service.log(
                    AuditEntryCreate(
                        action="provider_error",
                        target_type="archive",
                        metadata={"provider": p.name, "error": str(exc)},
                    )
                )

    stored = evidence_service.normalize_and_store(case.id, raw_results)
    if not stored:
        audit_service.log(
            AuditEntryCreate(
                action="archive_no_results",
                target_type="archive",
                target_id=str(case.id),
                metadata={"providers_attempted": len(providers), "targets_attempted": len(targets)},
            )
        )
        return ArchiveStartResponse(
            state="no_results",
            case_id=str(case.id),
            targets_attempted=len(targets),
            message=f"Tried {len(targets)} URL target(s) across {len(providers)} archive provider(s); no captures returned.",
            evidence=[],
        )

    return ArchiveStartResponse(
        state="completed",
        case_id=str(case.id),
        targets_attempted=len(targets),
        message=f"Archive returned {len(stored)} item(s) from {len(targets)} URL target(s).",
        evidence=stored,
    )


__all__ = ["run_web", "run_social", "run_archive", "ArchiveStartResponse"]
